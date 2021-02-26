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

class Cache[F[_]: Concurrent, K, V] private (
    inter: Ref[F, Map[K, Cache.State[F, V]]]
) {
  private type St = Cache.State[F, V]
  import Cache._

  private def retrieve(st: St): F[V] = st match {
    case Computing(result) => result.get
    case Value(result)     => result
  }

  def getOrElse(k: K)(build: F[V]): F[V] = Deferred[F, V].flatMap { computer =>
    inter
      .modify[F[V]] { m =>
        m.get(k) match {
          case Some(value) => m -> retrieve(value)
          case None =>
            m.updated(k, Computing(computer)) -> build
              .flatTap(computer.complete)
              .flatTap(value =>
                inter.update(_.updated(k, Value(value.pure[F])))
              )
        }
      }
      .flatten
  }
}

object Cache {
  sealed trait State[F[_], V]                           extends Product with Serializable
  case class Computing[F[_], V](result: Deferred[F, V]) extends State[F, V]
  case class Value[F[_], V](result: F[V])               extends State[F, V]

  def of[F[_]: Concurrent, K, V] =
    Ref.of[F, Map[K, State[F, V]]](Map.empty).map(new Cache(_))
}

abstract class Strategy[F[_]](
    val Log: Logger[F],
    cache: Cache[F, Artifact, Plan]
)(implicit
    F: cats.effect.Temporal[F]
) {

  def acceptable(v: Version): Boolean

  def suitableUpgrade(from: Version, to: Version): Boolean

  def getDependencies(
      versionedArtifact: VersionedArtifact
  ): F[Vector[VersionedArtifact]]

  def getSuggestions(artifact: Artifact): F[List[VersionedArtifact]]

  case class oops(msg: String) extends RuntimeException(msg)

  def planDependencies(
      artifact: VersionedArtifact,
      align: Artifact => Artifact
  ): F[Plan] = {
    val deps = getDependencies(artifact)
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

  // def cached[K, V](cache: Ref[F, Map[K, V]])(key: K)(build: F[V]) = {}

  def plan(artifact: Artifact, versionHint: Option[Version] = None): F[Plan] =
    cache.getOrElse(artifact) {
      getSuggestions(artifact)
        .flatMap {
          case Nil =>
            artifact.alternatives
              .foldM[F, Option[VersionedArtifact]](Option.empty) {
                case (Some(s), _) => Option(s).pure[F]
                case (None, next) =>
                  Log.info(s"Trying to retrieve dependencies from $next") *>
                    getSuggestions(next).flatMap { suggs =>
                      suggs
                        .filter(art => acceptable(art.version))
                        .sortBy(_.version)
                        .reverse
                        .headOption
                        .pure[F]
                    }
              }
              .flatMap {
                case None =>
                  oops(s"could not find any way to approximate $artifact")
                    .raiseError[F, Plan]
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
                )) || acceptable(art.version)
              )
              .sortBy(_.version)
              .reverse
              .headOption
              .fold(
                NoSuitableVersions(artifact, suggestions.map(_.version))
                  .raiseError[F, Plan]
              ) { found =>
                versionHint match {
                  case None =>
                    Plan(Vector(Use(artifact, found.version))).pure[F]
                  case Some(v) =>
                    if (v < found.version)
                      Plan(Vector(UpgradeDependency(artifact, found, v)))
                        .pure[F]
                    else Plan(Vector(Use(artifact, found.version))).pure[F]

                }
              }
        }
    }

}

class RazorYakNew(
    cs: CoursierWrap,
    cache: Cache[IO, Artifact, Plan],
    config: Config,
    atf: Artifact,
    logger: Logger[IO]
) {
  import coursier._
  def artifactToDependency(ver: VersionedArtifact) = Dependency(
    Module(
      Organization(ver.artifact.org),
      ModuleName(ver.artifact.completionName)
    ),
    ver.version.format
  )

  def dependencyToArtifact(
      dep: Dependency,
      base: VersionedArtifact
  ): Option[VersionedArtifact] = {
    val org       = dep.module.organization.value
    val rawModule = dep.module.name.value

    var finalModule = rawModule
    val ignoreOrgs  = Set("org.scala-js")

    if (
      finalModule.endsWith(base.artifact.axis.scalaVersion.raw) && !rawModule
        .contains(
          "scala3-library"
        ) && !ignoreOrgs.contains(org)
    ) {
      finalModule = finalModule.dropRight(
        base.artifact.axis.scalaVersion.raw.length + 1
      ) // underscore

      if (base.artifact.axis.platform == JS && finalModule.endsWith("_sjs1"))
        finalModule = finalModule.dropRight("_sjs1".length)

      if (
        base.artifact.axis.platform == NATIVE && finalModule.endsWith(
          "_native0.4"
        )
      )
        finalModule = finalModule.dropRight("_native0.4".length)

      Some(
        base.copy(
          artifact = base.artifact.copy(name = finalModule, org = org),
          version = semver4s
            .version(dep.version)
            .getOrElse(throw new RuntimeException("what"))
        )
      )
    } else None
  }

  def renderPlan(pl: Plan) = {
    pl.actions
      .map {
        case UpgradeDependency(atf, dep, from) =>
          s"[ ] Upgrade to ${atf.completionArtifact}${dep.version.format} from ${from.format}"
        case Use(artf, version) =>
          s"[x] Use ${artf.completionArtifact}${version.format}"
        case PublishFor(atf) =>
          s"[ ] Publish ${atf.completionArtifact} for ${atf.axis}"
      }
      .traverse(cats.effect.IO.println)
  }

  import cats.effect._

  def run = {
    val impl = new Strategy[IO](logger, cache) {
      override def getSuggestions(
          artifact: Artifact
      ): IO[List[VersionedArtifact]] = {
        cs.complete(artifact.completionArtifact).map(_._2).map { ver =>
          ver.toList.map(semver4s.version).collect { case Right(v) =>
            VersionedArtifact(artifact, v)
          }
        }
      }

      override def acceptable(v: Version) = true

      override def suitableUpgrade(from: Version, to: Version): Boolean = {
        import config._
        if (from.major < to.major && !allowMajorUpgrades) false
        else if (from.minor < to.minor && !allowMinorUpgrades) false
        else if (from.patch < to.patch && !allowPatchUpgrades) false
        else true
      }

      override def getDependencies(
          versionedArtifact: VersionedArtifact
      ): IO[Vector[VersionedArtifact]] = {
        cs.resolve(artifactToDependency(versionedArtifact)).map { res =>
          res.dependencies.toVector
            .flatMap(dependencyToArtifact(_, versionedArtifact))
        }
      }

    }

    impl.plan(atf).flatMap { plan =>
      renderPlan(plan.copy(actions = plan.actions.distinct)).void
    }
  }
}
