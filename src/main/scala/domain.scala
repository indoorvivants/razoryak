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

import razoryak.ArtifactVersion.Custom
import razoryak.ArtifactVersion.Semver
import semver4s.Version

sealed abstract class ScalaVersion(val raw: String)
    extends Product
    with Serializable {
  def previous: Option[ScalaVersion] = this match {
    case Scala211   => None
    case Scala212   => Some(Scala211)
    case Scala213   => Some(Scala212)
    case Scala3_M3  => Some(Scala213)
    case Scala3_RC1 => Some(Scala3_M3)
    case Scala3_RC2 => Some(Scala3_RC1)
    case Scala3_RC3 => Some(Scala3_RC2)
    case Scala3     => Some(Scala3_RC3)
  }
}
case object Scala211 extends ScalaVersion("2.11")

case object Scala212   extends ScalaVersion("2.12")
case object Scala213   extends ScalaVersion("2.13")
case object Scala3_M3  extends ScalaVersion("3.0.0-M3")
case object Scala3_RC1 extends ScalaVersion("3.0.0-RC1")
case object Scala3_RC2 extends ScalaVersion("3.0.0-RC2")
case object Scala3_RC3 extends ScalaVersion("3.0.0-RC3")
case object Scala3     extends ScalaVersion("3")

object ScalaVersion {
  def unapply(s: String): Option[ScalaVersion] = s match {
    case "2.13"      => Option(Scala213)
    case "2.12"      => Option(Scala212)
    case "2.11"      => Option(Scala211)
    case "3.0.0-M3"  => Option(Scala3_M3)
    case "3.0.0-RC1" => Option(Scala3_RC1)
    case "3.0.0-RC2" => Option(Scala3_RC2)
    case "3.0.0-RC3" => Option(Scala3_RC3)
    case "3"         => Option(Scala3)
    case other =>
      semver4s.parseVersion(other).toOption.flatMap { v =>
        if (v.major == 3L) Some(Scala3) else None
      }

  }
}

sealed trait ScalaPlatform
case object JVM    extends ScalaPlatform
case object NATIVE extends ScalaPlatform
case object JS     extends ScalaPlatform

sealed trait StateOfThings
case class Exists(config: Config, version: String) extends StateOfThings
case class NeedsCreation(config: Config, dependencies: Seq[StateOfThings])
    extends StateOfThings

case class Axis(scalaVersion: ScalaVersion, platform: ScalaPlatform) {
  def format: String = {
    val scala = scalaVersion match {
      case Scala211   => "2.11"
      case Scala212   => "2.12"
      case Scala213   => "2.13"
      case Scala3_M3  => "3.0.0-M3"
      case Scala3_RC1 => "3.0.0-RC1"
      case Scala3_RC2 => "3.0.0-RC2"
      case Scala3_RC3 => "3.0.0-RC3"
      case Scala3     => "3"
    }

    val plat = platform match {
      case JVM    => "JVM"
      case NATIVE => "Scala Native"
      case JS     => "Scala.js"
    }

    s"Scala $scala on $plat"

  }

}

case class VersionedArtifact(artifact: Artifact, version: ArtifactVersion) {
  override def toString = artifact.completionArtifact + version.format
}

object VersionedArtifact {
  implicit val ordering: Ordering[VersionedArtifact] =
    Ordering.by(va => (va.artifact, va.version.format))
}

sealed trait Action extends Product with Serializable
case class PublishFor(
    artifact: Artifact
) extends Action
case class UpgradeDependency(
    artifact: Artifact,
    dependency: VersionedArtifact,
    from: ArtifactVersion
)                                                            extends Action
case class Use(artifact: Artifact, version: ArtifactVersion) extends Action

sealed trait ArtifactVersion extends Product with Serializable {
  def format: String = this match {
    case Semver(version) => version.format
    case Custom(s)       => s
  }
}

object ArtifactVersion {
  case class Semver(version: Version) extends ArtifactVersion
  case class Custom(s: String)        extends ArtifactVersion

  private val ord = cats.Order[Version].toOrdering

  implicit val order: Order[ArtifactVersion] =
    Order.fromLessThan[ArtifactVersion] {
      case (Semver(l), Semver(r)) => ord.lt(l, r)
      case (l, r)                 => l.format < r.format
    }

  implicit val ordering: Ordering[ArtifactVersion] = order.toOrdering
}

sealed trait Problem extends Throwable with Product with Serializable
case class ArtifactDoesntExist(art: Artifact) extends Problem
case class NoSuitableVersions(art: Artifact, options: Seq[ArtifactVersion])
    extends Problem

case class Plan(actions: Vector[Action])

case class Artifact(
    tests: Boolean,
    org: String,
    name: String,
    axis: Axis
) {

  def completionName = {
    var base = name
    if (axis.platform == JS) base += "_sjs1"
    else if (axis.platform == NATIVE) base += "_native0.4"

    base += "_" + axis.scalaVersion.raw

    base
  }

  def completionArtifact = {
    org + ":" + completionName + ":"
  }

  def showArtifact = {
    org + ":" + name
  }

  def alternatives: List[Artifact] = {
    axis.scalaVersion.previous match {
      case None => Nil
      case Some(sv) =>
        val nt = copy(axis = axis.copy(scalaVersion = sv))
        nt +: nt.alternatives
    }
  }
}

object Artifact {

  implicit val ord: Ordering[Artifact] = Ordering.by(atf => (atf.org, atf.name))

  def fromConfig(c: Config) = Artifact(
    tests = c.tests,
    org = c.org,
    name = c.name,
    axis = Axis(c.scalaVersion, c.platform)
  )
}
