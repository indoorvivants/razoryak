package razoryak

import java.{io => jio}

import cats.syntax.all._

import cats.effect._

import weaver.Expectations
import weaver.SourceLocation
import weaver.TestName

case class UpgradePlan(
    use: Vector[Use],
    upgrade: Vector[UpgradeDependency],
    publish: Vector[PublishFor]
) {
  import Expectations.Helpers._
  def hasUpgrade(org: String, name: String)(implicit
      loc: SourceLocation
  ): Expectations =
    exists(upgrade)(v =>
      expect(v.artifact.org == org && v.artifact.name == name)
    )
  def hasPublish(org: String, name: String)(implicit
      loc: SourceLocation
  ): Expectations =
    exists(publish)(v =>
      expect(v.artifact.org == org && v.artifact.name == name)
    )

  def hasUse(org: String, name: String)(implicit
      loc: SourceLocation
  ): Expectations =
    exists(use)(v => expect(v.artifact.org == org && v.artifact.name == name))
}

trait Setup { self: weaver.IOSuite =>
  def resolutionTest(name: TestName, config: Config, filename: String)(
      check: UpgradePlan => Expectations
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

          UpgradePlan(use.result(), upgrade.result(), publish.result())
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
      coursierTrack = CoursierTrack.None,
      coursier = CoursierConfig.default
    )
}
