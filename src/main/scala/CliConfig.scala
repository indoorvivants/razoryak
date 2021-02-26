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

case class Config(
    tests: Boolean,
    log: Boolean,
    org: String,
    name: String,
    scalaVersion: ScalaVersion,
    platform: ScalaPlatform,
    allowMajorUpgrades: Boolean,
    allowMinorUpgrades: Boolean,
    allowPatchUpgrades: Boolean,
    allowSnapshots: Boolean,
    resolvers: List[String]
)

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
    .orFalse
  private val allowMinorUpgrades = Opts
    .flag(
      "allow-minor",
      help =
        "When looking for resolution, consider minor upgrades of dependencies (default: true)"
    )
    .orTrue
  private val allowPatchUpgrades = Opts
    .flag(
      "allow-patch",
      help =
        "When looking for resoltion, consider patch upgrades of dependencies (default: true)"
    )
    .orTrue
  private val allowSnapshots = Opts
    .flag(
      "allow-snapshots",
      help =
        "When looking for resoltion, consider snapshot versions of dependencies (default: true)"
    )
    .orTrue

  private val svOpt = Opts
    .option[String]("scala", "Scala version you wish to use")
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

  private val jvmOpt =
    Opts.flag("jvm", "search for JVM artifacts").as[ScalaPlatform](JVM)
  private val jsOpt =
    Opts.flag("js", "search for Scala.js artifacts").as[ScalaPlatform](JS)
  private val nativeOpt = Opts
    .flag("native", "search for Scala Native artifacts")
    .as[ScalaPlatform](NATIVE)

  private val platformOpt =
    jsOpt.orElse(nativeOpt).orElse(jvmOpt).withDefault(JVM)

  private val logOpt = Opts.flag("verbose", "log output", "v").orFalse
  private val configOpt =
    (
      testOpt,
      logOpt,
      orgOpt,
      nameOpt,
      svOpt,
      platformOpt,
      allowMajorUpgrades,
      allowMinorUpgrades,
      allowPatchUpgrades,
      allowSnapshots,
      resolversOpt
    ).mapN(Config.apply)

  val cmd = Command("razoryak", header = "howdy")(configOpt)
}
