package razoryak

import java.{io => jio}

import cats.syntax.all._

import cats.effect._

import weaver.Expectations
import weaver.TestName

trait Setup { self: weaver.IOSuite =>
  def resolutionTest(name: TestName, config: Config, filename: String)(
      check: (
          (Vector[Use], Vector[UpgradeDependency], Vector[PublishFor])
      ) => Expectations
  ) =
    loggedTest(name) { logger =>
      val coursier = CoursierWrap.Tracking.fromJsonFile(
        new jio.File(s"src/test/resources/$filename")
      )

      val log = new Logger[IO](logger.info(_))

      val atf = Artifact.fromConfig(config)

      (coursier, Cache.resource[IO, Artifact, Plan])
        .mapN(new RazorYak(_, _, config, atf, log))
        .use(_.plan)
        .map { plan =>
          val upgrade = plan.actions.collect { case u: UpgradeDependency =>
            u
          }

          val use = plan.actions.collect { case u: Use =>
            u

          }

          val publishFor = plan.actions.collect { case p: PublishFor =>
            p
          }

          (use, upgrade, publishFor)
        }
        .map(check)
    }

  def basic(org: String, name: String, scalaVersion: ScalaVersion) =
    Config(
      tests = false,
      log = true,
      org = org,
      name = name,
      scalaVersion = scalaVersion,
      platform = JVM,
      true,
      true,
      true,
      false,
      resolvers = Nil,
      CoursierTrack.None
    )
}
