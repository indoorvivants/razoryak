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

import cats.Show
import cats.syntax.all._

import cats.effect.Sync

class Logger[F[_]](fin: String => F[Unit]) {
  def debug[A](a: A)(implicit show: Show[A] = Show.fromToString[A]) = fin(
    Console.YELLOW + "[o]" + Console.RESET + " " + a.show
  )

  def info[A](a: A)(implicit show: Show[A] = Show.fromToString[A]) = fin(
    Console.GREEN + "[+]" + Console.RESET + " " + a.show
  )

  def error[A](a: A)(implicit show: Show[A] = Show.fromToString[A]) = fin(
    Console.RED + "[x]" + Console.RESET + " " + a.show
  )

}

object Logger {
  def from[F[_]: Sync](c: Config) = if (c.log)
    new Logger[F](_ => Sync[F].unit)
  else new Logger(s => Sync[F].delay(System.err.println(s)))
}
