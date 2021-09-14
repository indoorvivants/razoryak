/*
 * Copyright 2021 Anton Sviridov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package razoryak

import cats.kernel.Order
import cats.syntax.all._

import cats.effect._

import semver4s.Version

class Strategy(
    Log: Logger[IO],
    cache: Cache[IO, Artifact, Plan],
    cs: CoursierWrap,
    upgradeManager: UpgradeManager
) {

  case class oops(msg: String) extends RuntimeException(msg)

  def planDependencies(
      artifact: VersionedArtifact,
      align: Artifact => Artifact
  ): IO[Plan] = {
    val deps = cs.getDependencies(artifact)
    deps.flatMap { dependencies =>
      dependencies
        .filter(_ != artifact)
        .foldM(Plan(Vector.empty)) {
          case (current, VersionedArtifact(artifact, ver)) =>
            plan(align(artifact), Some(ver)).map { newPlan =>
              current.copy(actions = current.actions ++ newPlan.actions)
            }
        }
    }
  }

  implicit val od = Order[Version].toOrdering

  def bold(s: Any) = Console.BOLD + s.toString + Console.RESET

  def plan(artifact: Artifact, versionHint: Option[Version] = None): IO[Plan] =
    cache.getOrElse(artifact) {
      cs.getSuggestions(artifact)
        .flatMap {
          case Nil =>
            artifact.alternatives
              .foldM[IO, Option[VersionedArtifact]](Option.empty) {
                case (Some(s), _) => Option(s).pure[IO]
                case (None, next) =>
                  Log.info(s"Trying to retrieve dependencies from $next") *>
                    cs.getSuggestions(next).flatMap { suggs =>
                      suggs
                        .filter(art => upgradeManager.acceptable(art.version))
                        .sortBy(_.version)
                        .reverse
                        .headOption
                        .pure[IO]
                    }
              }
              .flatMap {
                case None =>
                  IO.raiseError(
                    oops(s"could not find any way to approximate $artifact")
                  )
                case Some(versioned) =>
                  val align: Artifact => Artifact = _.copy(
                    axis = artifact.axis
                  )
                  Log.info(
                    s"We will construct ${bold(artifact.completionArtifact + "<FUTURE>")} " +
                      s"by checking the dependencies of ${bold(versioned)}"
                  ) *> planDependencies(versioned, align).map { pl =>
                    pl.copy(
                      pl.actions :+ PublishFor(
                        artifact
                      )
                    )
                  }
              }

          case suggestions =>
            suggestions
              .filter(art =>
                (versionHint.nonEmpty && versionHint.contains(
                  art.version
                )) || upgradeManager.acceptable(art.version)
              )
              .sortBy(_.version)
              .reverse
              .headOption
              .fold(
                IO.raiseError[Plan](
                  NoSuitableVersions(artifact, suggestions.map(_.version))
                )
              ) { found =>
                versionHint match {
                  case None =>
                    Plan(Vector(Use(artifact, found.version))).pure[IO]
                  case Some(v) =>
                    if (v < found.version)
                      Plan(Vector(UpgradeDependency(artifact, found, v)))
                        .pure[IO]
                    else Plan(Vector(Use(artifact, found.version))).pure[IO]

                }
              }
        }
    }

}

class RazorYak(
    cs: CoursierWrap,
    cache: Cache[IO, Artifact, Plan],
    config: Config,
    logger: Logger[IO]
) {

  val atf = Artifact.fromConfig(config)

  def renderPlan(pl: Plan) = {
    pl.actions
      .map {
        case UpgradeDependency(atf, dep, from) =>
          s"[ ] Upgrade to ${atf.showArtifact}${dep.version.format} from ${from.format}"
        case Use(artf, version) =>
          s"[x] Use ${artf.showArtifact}${version.format}"
        case PublishFor(atf) =>
          s"[ ] Publish ${atf.showArtifact} for ${atf.axis}"
      }
      .traverse(cats.effect.IO.println)
  }

  def plan = {
    val upgradeManager = UpgradeManager.fromConfig(config.upgradePolicy)
    val impl           = new Strategy(logger, cache, cs, upgradeManager)
    impl.plan(atf).map(pl => pl.copy(actions = pl.actions.distinct))
  }

  def printPlan = plan.flatMap(renderPlan).void
}
