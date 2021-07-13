package izumi.functional.bio.impl

import izumi.functional.bio.{Mutex2, PrimitivesM2, RefM2}
import zio._


object PrimitivesMZio extends PrimitivesMZio

class PrimitivesMZio extends PrimitivesM2[IO] {
  override def mkRefM[A](a: A): IO[Nothing, RefM2[IO, A]] = {
    RefM.make(a).map(RefM2.fromZIO)
  }
  override def mkMutex[E, A]: IO[Nothing, Mutex2[IO]] = {
    Mutex2.createFromBIO
  }
}
