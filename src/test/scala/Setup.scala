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

      (coursier, Cache.resource[IO, Artifact, Plan])
        .mapN(new RazorYak(_, _, config, log))
        .use(_.plan)
        .map { plan =>
          val use     = Vector.newBuilder[Use]
          val upgrade = Vector.newBuilder[UpgradeDependency]
          val publish = Vector.newBuilder[PublishFor]

          plan.actions.foreach {
            case u: Use               => use.addOne(u)
            case u: UpgradeDependency => upgrade.addOne(u)
            case p: PublishFor        => publish.addOne(p)
          }

          (use.result(), upgrade.result(), publish.result())
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
      upgradePolicy = UpgradePolicy.minorAtMost,
      resolvers = Nil,
      coursierTrack = CoursierTrack.None
    )
}
