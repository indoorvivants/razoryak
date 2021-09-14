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

import io.circe._
import semver4s.Version

trait JsonProtocol {
  private val platMapping = Map[ScalaPlatform, String](
    JVM    -> "jvm",
    NATIVE -> "native",
    JS     -> "js"
  )
  private val inversePlatMapping = platMapping.toSeq.map(_.swap).toMap

  implicit val platformEncode =
    Encoder[String].contramap[ScalaPlatform](platMapping)
  implicit val scalaVersionEncode =
    Encoder[String].contramap[ScalaVersion](_.raw)

  implicit val axisEncode: Encoder[Axis] =
    Encoder.forProduct2("scala", "platform")(a => (a.scalaVersion, a.platform))

  implicit val artifactEncoder: Encoder[Artifact] =
    Encoder.forProduct4("tests", "org", "name", "axis") { atf =>
      (atf.tests, atf.org, atf.name, atf.axis)
    }

  implicit val versionEncoder: Encoder[Version] =
    Encoder[String].contramap[Version](_.format)

  implicit val versionedArtifactEncoder: Encoder[VersionedArtifact] =
    Encoder.forProduct2("artifact", "version") { va =>
      (va.artifact, va.version)
    }

  implicit val platformDecode: Decoder[ScalaPlatform] =
    Decoder[String].map(inversePlatMapping)
  implicit val scalaVersionDecode: Decoder[ScalaVersion] =
    Decoder[String].emap(
      ScalaVersion.unapply(_).toRight("Invalid scala version")
    )

  implicit val axisDecde: Decoder[Axis] =
    Decoder.forProduct2("scala", "platform")(Axis.apply)

  implicit val artifactDecoder: Decoder[Artifact] =
    Decoder.forProduct4("tests", "org", "name", "axis")(Artifact.apply)

  implicit val versionDecoder: Decoder[Version] =
    Decoder[String].emap(semver4s.parseVersion(_).left.map(_.toString))

  implicit val versionedArtifactDecoder: Decoder[VersionedArtifact] =
    Decoder.forProduct2("artifact", "version")(VersionedArtifact.apply)
}

object JsonProtocol extends JsonProtocol
