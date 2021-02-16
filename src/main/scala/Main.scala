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

import scala.collection.immutable.Nil
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import cats.Show
import cats.data.Validated
import cats.effect._
import cats.syntax.all._
import coursier._
import coursier.complete.Complete
import coursier.core.Configuration
import coursier.util.Task

object Main extends IOApp {
  def run(args: List[String]) = Config.cmd.parse(args) match {
    case Left(help) =>
      IO.consoleForIO.errorln(help).as(ExitCode.Error)
    case Right(config) =>
      CoursierWrap.create
        .map(new RazorYak(_, Logger.from(config)))
        .use(rz =>
          IO.ref(Map.empty[Config, StateOfThings])
            .flatMap { rf => rz.find(config, rf) }
            .flatMap(rz.printOutPlan)
        )
        .as(ExitCode.Success)
  }
}

sealed abstract class ScalaVersion(val raw: String)
    extends Product
    with Serializable {
  def previous: Option[ScalaVersion] = this match {
    case Scala211   => None
    case Scala212   => Some(Scala211)
    case Scala213   => Some(Scala212)
    case Scala3_M3  => Some(Scala213)
    case Scala3_RC1 => Some(Scala3_M3)
  }
}
case object Scala211 extends ScalaVersion("2.11")

case object Scala212   extends ScalaVersion("2.12")
case object Scala213   extends ScalaVersion("2.13")
case object Scala3_M3  extends ScalaVersion("3.0.0-M3")
case object Scala3_RC1 extends ScalaVersion("3.0.0-RC1")

object ScalaVersion {
  def unapply(s: String): Option[ScalaVersion] = s match {
    case "2.13"      => Option(Scala213)
    case "2.12"      => Option(Scala212)
    case "2.11"      => Option(Scala211)
    case "3.0.0-M3"  => Option(Scala3_M3)
    case "3.0.0-RC1" => Option(Scala3_RC1)
  }
}

sealed trait ScalaPlatform
case object JVM    extends ScalaPlatform
case object NATIVE extends ScalaPlatform
case object JS     extends ScalaPlatform

case class Config(
    tests: Boolean,
    log: Boolean,
    org: String,
    name: String,
    scalaVersion: ScalaVersion,
    platform: ScalaPlatform
)

object Config {
  import com.monovore.decline._
  private val testOpt = Opts.flag("tests", "t").orFalse
  private val orgOpt  = Opts.argument[String]("org")
  private val nameOpt = Opts.argument[String]("name")

  private val svOpt = Opts
    .option[String]("scala", "Scala version")
    .mapValidated { sv =>
      ScalaVersion.unapply(sv) match {
        case Some(value) => Validated.valid(value)
        case None        => Validated.invalidNel(s"Unknown scala version $sv")
      }
    }

  private val jvmOpt =
    Opts.flag("jvm", "search for JVM artifacts").as[ScalaPlatform](JVM)
  private val jsOpt =
    Opts.flag("js", "search for Scala.js artifacts").as[ScalaPlatform](JS)
  private val nativeOpt = Opts
    .flag("native", "search for Scala Native artifacts")
    .as[ScalaPlatform](NATIVE)

  val platformOpt = jsOpt.orElse(nativeOpt).orElse(jvmOpt).withDefault(JVM)

  val logOpt = Opts.flag("verbose", "log output", "v").orFalse
  val configOpt =
    (testOpt, logOpt, orgOpt, nameOpt, svOpt, platformOpt).mapN(Config.apply)

  val cmd = Command("razoryak", header = "howdy")(configOpt)
}

class Logger(fin: String => IO[Unit] = IO.consoleForIO.errorln[String] _) {
  def debug[A](a: A)(implicit show: Show[A] = Show.fromToString[A]) = fin(
    Console.YELLOW + "[o]" + Console.RESET + " " + a.show
  )

  def yay[A](a: A)(implicit show: Show[A] = Show.fromToString[A]) = fin(
    Console.GREEN + "[+]" + Console.RESET + " " + a.show
  )

  def ohno[A](a: A)(implicit show: Show[A] = Show.fromToString[A]) = fin(
    Console.RED + "[x]" + Console.RESET + " " + a.show
  )

}

object Logger {
  def from(c: Config) = if (c.log) new Logger() else new Logger(_ => IO.unit)
}

sealed trait StateOfThings
case class Exists(config: Config, version: String) extends StateOfThings
case class NeedsCreation(config: Config, dependencies: Seq[StateOfThings])
    extends StateOfThings
class RazorYak(cs: CoursierWrap, logger: Logger) {
  private def error(msg: String) = new RuntimeException(msg)
  private def fail(msg: String)  = IO.raiseError(error(msg))

  private def completionName(config: Config) = {
    var base = config.name
    if (config.platform == JS) base += "_sjs1"
    else if (config.platform == NATIVE) base += "_native0.4"

    base += "_" + config.scalaVersion.raw

    base
  }

  private def completionArtifact(config: Config) = {
    config.org + ":" + completionName(config) + ":"
  }

  private def createDep(config: Config, ver: String) = {
    Dependency(
      Module(
        Organization(config.org),
        ModuleName(completionName(config))
      ),
      ver
    ).withConfiguration(
      if (config.tests) Configuration.test else Configuration.empty
    )
  }

  import logger._
  import cats.data.Chain

  def printOutPlan(plan: StateOfThings): IO[Unit] = {
    def go(plan: StateOfThings, ct: Chain[String]): IO[Chain[String]] =
      IO(plan).flatMap {
        case _: Exists => IO.pure(ct)
        case NeedsCreation(conf, dependencies) =>
          val action =
            s"* [ ] you'll need to publish ${conf.name} for ${conf.scalaVersion.raw}"
          if (dependencies.isEmpty) {
            if (ct.contains(action)) ct.pure[IO]
            else ct.append(action).pure[IO]
          } else {
            val newCt = if (ct.contains(action)) Chain.empty else Chain(action)
            dependencies
              .foldLeftM(newCt) { case (c, st) =>
                go(st, c)
              }
              .map(ct => if (ct.contains(action)) ct else ct.append(action))
          }
      }

    go(plan, Chain.empty[String])
      .flatTap(actions =>
        IO.println("❌ Here's a list of actions you need to do (in this order)")
          .whenA(actions.nonEmpty) *>
          IO.println("✅ It's all good, you don't need to do anything")
            .whenA(actions.isEmpty)
      )
      .flatMap(_.traverse_(IO.println))
  }

  def find(
      config: Config,
      rf: Ref[IO, Map[Config, StateOfThings]]
  ): IO[StateOfThings] = rf.get.map(_.get(config)).flatMap {
    case Some(result) =>
      debug(s"$config was found in cache!") *> result.pure[IO]
    case None =>
      val nm = completionArtifact(config)
      for {
        comp <- cs.complete(nm)
        _    <- debug(show"Trying $nm ...")
        result <-
          comp._2.toList.reverse match {
            case Nil =>
              ohno(s"$nm doesn't seem to exist :(") *> planShit(config, rf)
                .flatTap(st => rf.update(_.updated(config, st)))
            case head :: _ =>
              val result = Exists(config, head)

              rf.update(_.updated(config, result))
                .as(result)
          }

      } yield result
  }

  def isScalaDependency(dep: Dependency, config: Config) =
    dep.module.name.value.endsWith("_" + config.scalaVersion.raw)

  def createScalaConfig(dep: Dependency, base: Config): Option[Config] = {
    val org       = dep.module.organization.value
    val rawModule = dep.module.name.value

    var finalModule = rawModule

    if (
      finalModule.endsWith(base.scalaVersion.raw) && !rawModule.contains(
        "scala3-library"
      )
    ) {
      finalModule =
        finalModule.dropRight(base.scalaVersion.raw.length + 1) // underscore

      if (base.platform == JS)
        finalModule = finalModule.dropRight("_sjs1".length)

      Some(base.copy(name = finalModule, org = org))
    } else None
  }

  def planShit(
      desiredConfig: Config,
      store: Ref[IO, Map[Config, StateOfThings]]
  ): IO[StateOfThings] = {
    desiredConfig.scalaVersion.previous match {
      case Some(previous) =>
        val currentConfig = desiredConfig.copy(scalaVersion = previous)
        for {
          versions <-
            debug(
              s"Attempting to check project's dependencies on $previous"
            ) *> cs
              .complete(
                completionArtifact(currentConfig)
              )
              .map(_._2.reverse)

          _ <- debug(
            s"Discovered following versions $versions for $currentConfig"
          )
          results <- versions match {
            case latest :: _ =>
              val dep = createDep(currentConfig, latest)

              cs.resolve(dep)
                .flatMap { resolution =>
                  val filteredDeps = resolution.dependencies.toList
                    .filter(_.module != dep.module)
                    .flatMap(createScalaConfig(_, currentConfig))
                    .map(_.copy(scalaVersion = desiredConfig.scalaVersion))

                  debug(
                    s"Found following dependencies $filteredDeps for $currentConfig"
                  ) *>
                    filteredDeps
                      .traverse { d =>
                        debug(s"Recursing into $d") *> find(d, store)
                      }
                      .map(NeedsCreation(desiredConfig, _))

                }
            case _ =>
              debug(
                s"Going to try ${currentConfig.name} on an older Scala ${currentConfig.scalaVersion.previous}..."
              ) >> planShit(currentConfig, store)
          }
        } yield results
      case None =>
        fail("And Alexander wept, for there were no more Scala versions to try")
    }
  }
}

trait CoursierWrap {
  def resolve(artifact: Dependency): IO[Resolution]
  def complete(input: String): IO[(Int, Seq[String])]
}

object CoursierWrap {

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
