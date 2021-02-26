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

import cats.effect.IO

object Output {

  import cats.data.Chain

  def printOutPlan(plan: StateOfThings): IO[Unit] = {
    def go(plan: StateOfThings, ct: Chain[String]): IO[Chain[String]] =
      IO(plan).flatMap {
        case _: Exists => IO.pure(ct)
        case NeedsCreation(conf, dependencies) =>
          val platformSuffix =
            if (conf.platform == JVM) ""
            else if (conf.platform == JS)
              Console.BOLD + " (Scala.js)" + Console.RESET
            else if (conf.platform == NATIVE)
              Console.BOLD + " (Scala Native)" + Console.RESET

          val action =
            s"* [ ] you'll need to publish ${conf.org}:${conf.name} for ${conf.scalaVersion.raw}$platformSuffix"
          if (dependencies.isEmpty) {
            if (ct.contains(action)) ct.pure[IO]
            else ct.append(action).pure[IO]
          } else {
            dependencies
              .foldLeftM(ct) { case (c, st) =>
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
}
