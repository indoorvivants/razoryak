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

class Cache[F[_]: Concurrent, K, V] private (
    inter: Ref[F, Map[K, Cache.State[F, V]]]
) {
  private type St = Cache.State[F, V]
  import Cache._

  private def retrieve(st: St): F[V] = st match {
    case Computing(result) => result.get
    case Value(result)     => result
  }

  def getOrElse(k: K)(build: F[V]): F[V] = Deferred[F, V].flatMap { computer =>
    inter
      .modify[F[V]] { m =>
        m.get(k) match {
          case Some(value) => m -> retrieve(value)
          case None =>
            m.updated(k, Computing(computer)) -> build
              .flatTap(computer.complete)
              .flatTap(value =>
                inter.update(_.updated(k, Value(value.pure[F])))
              )
        }
      }
      .flatten
  }
}

object Cache {
  sealed trait State[F[_], V] extends Product with Serializable
  case class Computing[F[_], V](result: Deferred[F, V]) extends State[F, V]
  case class Value[F[_], V](result: F[V])               extends State[F, V]

  def of[F[_]: Concurrent, K, V] =
    Ref.of[F, Map[K, State[F, V]]](Map.empty).map(new Cache(_))

  def resource[F[_]: Concurrent, K, V] = Resource.eval(of[F, K, V])
}
