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

import cats.syntax.all._

import cats.effect._

object Main extends IOApp {
  def run(args: List[String]) = Config.cmd.parse(args) match {
    case Left(help) =>
      IO.consoleForIO.errorln(help).as(ExitCode.Error)

    case Right(config) =>
      val coursier = config.coursierTrack match {
        case CoursierTrack.None => CoursierWrap.create(config.coursier)
        case CoursierTrack.WriteTo(path) =>
          CoursierWrap
            .create(config.coursier)
            .flatMap(CoursierWrap.Tracking.toJsonFile(path))

        case CoursierTrack.ReproduceFrom(path) =>
          CoursierWrap.Tracking.fromJsonFile(path)
      }

      (coursier, Resource.eval(Cache.of[IO, Artifact, Plan]))
        .mapN(new RazorYak(_, _, config, Logger.from[IO](config)))
        .use(_.printPlan)
        .as(ExitCode.Success)
  }
}
