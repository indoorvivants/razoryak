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
import java.io.FileWriter

import scala.concurrent._
import scala.io.Source

import cats.syntax.all._

import cats.effect._

import coursier._
import coursier.complete.Complete
import coursier.util.Task

import io.circe.Decoder
import io.circe.Encoder

trait CoursierWrap {

  import CoursierWrap.{dependencyToArtifact, artifactToDependency}

  def getSuggestions(
      artifact: Artifact
  ): IO[Vector[VersionedArtifact]] = {
    complete(artifact.completionArtifact).map(_._2).map { ver =>
      ver.toVector.map(semver4s.version).collect { case Right(v) =>
        VersionedArtifact(artifact, v)
      }
    }
  }

  def getDependencies(
      versionedArtifact: VersionedArtifact
  ): IO[Vector[VersionedArtifact]] = {
    resolve(artifactToDependency(versionedArtifact)).map { res =>
      res.dependencies.toVector
        .flatMap(dependencyToArtifact(_, versionedArtifact))
    }
  }

  protected def resolve(artifact: Dependency): IO[Resolution]
  protected def complete(input: String): IO[(Int, Seq[String])]
}

object CoursierWrap {
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

  class Tracking(
      impl: CoursierWrap,
      trackResolve: (VersionedArtifact, Vector[VersionedArtifact]) => IO[Unit],
      trackComplete: (Artifact, Vector[VersionedArtifact]) => IO[Unit]
  ) extends CoursierWrap {
    override def complete(input: String): IO[(Int, Seq[String])] =
      impl.complete(input)

    override def resolve(artifact: Dependency): IO[Resolution] =
      impl.resolve(artifact)

    override def getSuggestions(
        artifact: Artifact
    ): IO[Vector[VersionedArtifact]] =
      super.getSuggestions(artifact).flatTap(trackComplete(artifact, _))

    override def getDependencies(
        versionedArtifact: VersionedArtifact
    ): IO[Vector[VersionedArtifact]] =
      super
        .getDependencies(versionedArtifact)
        .flatTap(trackResolve(versionedArtifact, _))
  }

  object Tracking extends JsonProtocol {
    private case class Results(
        resolves: Map[VersionedArtifact, Vector[VersionedArtifact]],
        completes: Map[Artifact, Vector[VersionedArtifact]]
    )

    implicit private val encoder: Encoder[Results] =
      Encoder.forProduct2("resolves", "completes") { res =>
        (res.resolves.toList.sortBy(_._1), res.completes.toList.sortBy(_._1))
      }

    implicit private val decoder: Decoder[Results] =
      Decoder.forProduct2("resolves", "completes") {
        (
            resolves: Seq[(VersionedArtifact, Vector[VersionedArtifact])],
            completes: Seq[(Artifact, Vector[VersionedArtifact])]
        ) =>
          Results(resolves.toMap, completes.toMap)
      }

    import io.circe.syntax._

    def toJsonFile(
        file: java.io.File
    ): CoursierWrap => Resource[IO, CoursierWrap] = impl => {
      Resource.eval(IO.ref(Results(Map.empty, Map.empty))).flatMap { state =>
        val trackResolve =
          (dep: VersionedArtifact, res: Vector[VersionedArtifact]) =>
            state.update(results =>
              results.copy(resolves = results.resolves.updated(dep, res))
            )
        val trackComplete =
          (partial: Artifact, res: Vector[VersionedArtifact]) =>
            state.update(results =>
              results.copy(completes = results.completes.updated(partial, res))
            )

        val acquire: IO[CoursierWrap] =
          IO.pure(new Tracking(impl, trackResolve, trackComplete))

        val release = (_: CoursierWrap) =>
          state.get.flatMap { results =>
            val writer = Resource.fromAutoCloseable(IO(new FileWriter(file)))

            writer
              .use(w => IO(w.append(results.asJson.noSpacesSortKeys)))
              .flatMap(_ => IO.println(s"Wrote results to $file"))
          }

        Resource.make(acquire)(release)
      }
    }

    class Reproducing(results: Results) extends CoursierWrap {
      override def getDependencies(
          versionedArtifact: VersionedArtifact
      ): IO[Vector[VersionedArtifact]] =
        IO.fromOption(results.resolves.get(versionedArtifact))(
          new RuntimeException(
            s"Error! Unexpected resolution $versionedArtifact"
          )
        )

      override def getSuggestions(
          artifact: Artifact
      ): IO[Vector[VersionedArtifact]] =
        IO.fromOption(results.completes.get(artifact))(
          new RuntimeException(s"Error! Unexpected completion $artifact")
        )

      override protected def complete(input: String): IO[(Int, Seq[String])] =
        ???
      override protected def resolve(artifact: Dependency): IO[Resolution] = ???
    }

    def fromJsonFile(file: java.io.File): Resource[IO, CoursierWrap] = {

      val contents = IO(
        Source.fromFile(file).getLines().mkString(System.lineSeparator())
      )

      Resource.eval(
        contents
          .flatMap { text =>
            IO.fromEither(io.circe.parser.decode[Results](text))
          }
          .map[CoursierWrap](res => new Reproducing(res))
      )

    }

  }

  private def fut[A](f: ExecutionContext => Future[A]) = IO.fromFuture {
    IO.executionContext.map { ec =>
      f(ec)
    }
  }

  private class Impl(resolve: Resolve[Task], compl: Complete[Task])
      extends CoursierWrap {

    override def resolve(artifact: Dependency): IO[Resolution] = fut {
      implicit ec =>
        resolve.addDependencies(artifact).future()
    }

    def complete(inp: String) = fut { implicit ec =>
      compl.withInput(inp).complete().future()
    }
  }

  def create: Resource[IO, CoursierWrap] = {
    val resources = for {
      cache  <- IO(coursier.cache.FileCache())
      resolv <- IO(Resolve(cache))
      compl  <- IO(Complete(cache))
    } yield new Impl(resolv, compl)

    Resource.eval(resources)
  }
}
