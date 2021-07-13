package izumi.functional.bio.impl

import izumi.functional.bio.{Bracket2, Mutex2, Primitives2, PrimitivesM2, RefM2}

class PrimitivesMFromBIO[F[+_, +_]: Bracket2: Primitives2] extends PrimitivesM2[F] {
  override def mkRefM[A](a: A): F[Nothing, RefM2[F, A]] = {
    RefM2.createFromBIO(a)
  }
  override def mkMutex[E, A]: F[Nothing, Mutex2[F]] = {
    Mutex2.createFromBIO
  }
}
