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

trait UpgradeManager {

  def acceptable(v: ArtifactVersion): Boolean

  def suitableUpgrade(from: ArtifactVersion, to: ArtifactVersion): Boolean
}

object UpgradeManager {
  def fromConfig(config: UpgradePolicy) = new UpgradeManager {

    def acceptable(v: ArtifactVersion) = true

    def suitableUpgrade(from: ArtifactVersion, to: ArtifactVersion): Boolean = {
      (from, to) match {
        case (ArtifactVersion.Semver(from), ArtifactVersion.Semver(to)) =>
          import config._
          if (from.major < to.major && !allowMajor) false
          else if (from.minor < to.minor && !allowMinor) false
          else if (from.patch < to.patch && !allowPatch) false
          else true
        case _ => true // TODO: log that comparison has been skipped
      }
    }

  }

}
