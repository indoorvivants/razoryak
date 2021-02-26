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
import scala.concurrent._

import cats.effect._

import coursier._
import coursier.complete.Complete
import coursier.util.Task

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
