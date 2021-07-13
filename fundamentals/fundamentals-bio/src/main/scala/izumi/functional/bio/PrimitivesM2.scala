package izumi.functional.bio

import izumi.functional.bio.data.~>>
import izumi.functional.bio.impl.PrimitivesMFromBIO
import izumi.fundamentals.orphans.`zio.ZIO`

trait PrimitivesM2[F[+_, +_]] extends PrimitivesMInstances {
  def mkRefM[A](a: A): F[Nothing, RefM2[F, A]]
  def mkMutex[E, A]: F[Nothing, Mutex2[F]]
}
object PrimitivesM2 {
  def apply[F[+_, +_]: PrimitivesM2]: PrimitivesM2[F] = implicitly

  implicit final class PrimitivesM2Ops[F[+_, +_]](private val self: PrimitivesM2[F]) extends AnyVal {
    def mapK[G[+_, +_]](fg: F ~>> G, gf: G ~>> F)(implicit G: Functor2[G]): PrimitivesM2[G] = new PrimitivesM2[G] {
      def mkRefM[A](a: A): G[Nothing, RefM2[G, A]] = fg(self.mkRefM(a)).map(_.mapK(fg, gf))
      def mkMutex[E, A]: G[Nothing, Mutex2[G]] = fg(self.mkMutex).map(_.mapK(fg, gf))
    }
  }
}

private[bio] sealed trait PrimitivesMInstances
object PrimitivesMInstances extends PrimitivesMLowPriorityInstances {
  @inline implicit def PrimitivesZio[F[-_, +_, +_]: `zio.ZIO`]: PrimitivesM3[F] = impl.PrimitivesMZio.asInstanceOf[PrimitivesM3[F]]
}

sealed trait PrimitivesMLowPriorityInstances {
  @inline implicit def PrimitivesFromBIO[F[+_, +_]: Bracket2: Primitives2]: PrimitivesM2[F] = new PrimitivesMFromBIO[F]
}
