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

import cats.data._
import cats.implicits._

import com.monovore.decline._

sealed trait CoursierTrack
object CoursierTrack {
  case class WriteTo(file: java.io.File)       extends CoursierTrack
  case class ReproduceFrom(file: java.io.File) extends CoursierTrack
  case object None                             extends CoursierTrack
}

case class UpgradePolicy(
    allowMajor: Boolean,
    allowMinor: Boolean,
    allowPatch: Boolean,
    allowSnapshots: Boolean
)

object UpgradePolicy {
  def noUpgrades = UpgradePolicy(false, false, false, false)

  def allowAll = allowAllStable.copy(allowSnapshots = true)
  def allowAllStable = UpgradePolicy(
    allowMajor = true,
    allowMinor = true,
    allowPatch = true,
    allowSnapshots = false
  )

  def minorAtMost = noUpgrades.copy(allowMinor = true, allowPatch = true)
}

case class Config(
    tests: Boolean,
    log: Boolean,
    org: String,
    name: String,
    scalaVersion: ScalaVersion,
    platform: ScalaPlatform,
    coursierTrack: CoursierTrack,
    upgradePolicy: UpgradePolicy,
    coursier: CoursierConfig
)

case class CoursierConfig(
    disableDefaults: Boolean,
    resolvers: Seq[String],
    cacheMethod: Option[String]
)

object CoursierConfig {
  val default = CoursierConfig(
    disableDefaults = false,
    resolvers = Seq.empty,
    cacheMethod = None
  )
}

object Config {
  private val testOpt = Opts.flag("tests", "t").orFalse
  private val orgOpt  = Opts.argument[String]("org")
  private val nameOpt = Opts.argument[String]("name")
  private val allowMajorUpgrades = Opts
    .flag(
      "allow-major",
      help =
        "When looking for resolution, consider major upgrades of dependencies (default: false)"
    )
    .orNone
    .map(_.contains(()))

  private val allowMinorUpgrades = Opts
    .flag(
      "allow-minor",
      help =
        "When looking for resolution, consider minor upgrades of dependencies (default: true)"
    )
    .orNone
    .map(_.contains(()))
  private val allowPatchUpgrades = Opts
    .flag(
      "allow-patch",
      help =
        "When looking for resoltion, consider patch upgrades of dependencies (default: true)"
    )
    .orNone
    .map(_.contains(()))
  private val allowSnapshots = Opts
    .flag(
      "allow-snapshots",
      help =
        "When looking for resoltion, consider snapshot versions of dependencies (default: false)"
    )
    .orNone
    .map(_.contains(()))

  private val upgradePolicy =
    (allowMajorUpgrades, allowMinorUpgrades, allowPatchUpgrades, allowSnapshots)
      .mapN(UpgradePolicy.apply)
      .withDefault(UpgradePolicy.minorAtMost)

  private val svOpt = Opts
    .option[String](
      "scala",
      "Scala version you wish to use\nExamples: 2.13, 2.12, 3, 3.0.0-RC3"
    )
    .mapValidated { sv =>
      ScalaVersion.unapply(sv) match {
        case Some(value) => Validated.valid(value)
        case None        => Validated.invalidNel(s"Unknown scala version $sv")
      }
    }
  private val resolversOpt = Opts
    .options[String](
      "resolver",
      short = "r",
      help = "Resolvers to use, in coursier format"
    )
    .orEmpty

  private val cacheModeOpt = Opts
    .option[String](
      "mode",
      help = "Download mode, passed directly to coursier\n" +
        "offline|update-changing|update|missing|force\n" + "default is 'missing'"
    )
    .orNone

  private val disableDefaultsOpt = Opts
    .flag(
      "no-default",
      help = "Don't add default resolvers (i.e. maven central and ivy2local)"
    )
    .orFalse

  val coursierConfig =
    (disableDefaultsOpt, resolversOpt, cacheModeOpt).mapN(CoursierConfig.apply)

  private val jvmOpt =
    Opts.flag("jvm", "search for JVM artifacts").as[ScalaPlatform](JVM)
  private val jsOpt =
    Opts.flag("js", "search for Scala.js artifacts").as[ScalaPlatform](JS)
  private val nativeOpt = Opts
    .flag("native", "search for Scala Native artifacts")
    .as[ScalaPlatform](NATIVE)

  private val platformOpt =
    jsOpt.orElse(nativeOpt).orElse(jvmOpt).withDefault(JVM)

  val trackCoursierOpt = Opts
    .option[String](
      "track-coursier",
      help = "Path to a file where to dump traces of coursier resolution"
    )
    .map[CoursierTrack](s => CoursierTrack.WriteTo(new java.io.File(s)))

  val reproduceCoursierOpt = Opts
    .option[String](
      "replay-coursier",
      help = "Path to a file with coursier trace to reproduce"
    )
    .map[CoursierTrack](s => CoursierTrack.ReproduceFrom(new java.io.File(s)))

  val coursierTrackOpt =
    (trackCoursierOpt orElse reproduceCoursierOpt)
      .withDefault(
        CoursierTrack.None
      )

  private val logOpt = Opts.flag("verbose", "log output", "v").orFalse
  private val configOpt =
    (
      testOpt,
      logOpt,
      orgOpt,
      nameOpt,
      svOpt,
      platformOpt,
      coursierTrackOpt,
      upgradePolicy,
      coursierConfig
    ).mapN(Config.apply)

  val readme =
    s"""
  |Welcome to razoryak 
  |
  |To get a test of what this tool does, ask it to upgrade itself to Scala 3:
  |
  |cs launch com.indoorvivants::razoryak:0.0.4 -- com.indoorvivants razoryak --scala 3
   """.stripMargin.stripMargin(' ')

  val cmd = Command("razoryak", header = readme)(configOpt)
}
