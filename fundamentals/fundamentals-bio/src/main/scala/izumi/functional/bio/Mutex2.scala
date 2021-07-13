package izumi.functional.bio

import izumi.functional.bio.data.{Morphism2, ~>>}

trait Mutex2[F[+_, +_]] {
  def bracket[E, A](f: F[E, A]): F[E, A]
  def bracket_[E, A](f: F[E, A]): F[E, Unit]
}

object Mutex2 {
  def createFromBIO[F[+_, +_]: Bracket2: Primitives2]: F[Nothing, Mutex2[F]] = {
    F.mkSemaphore(1).map {
      semaphore =>
        new Mutex2[F] {
          override def bracket[E, A](f: F[E, A]): F[E, A] = {
            F.bracket(semaphore.acquire)(_ => semaphore.release)(_ => f)
          }
          override def bracket_[E, A](f: F[E, A]): F[E, Unit] = {
            F.bracket(semaphore.acquire)(_ => semaphore.release)(_ => f).void
          }
        }
    }
  }

  implicit final class Mutex2Ops[F[+_, +_]](private val self: Mutex2[F]) extends AnyVal {
    def mapK[G[+_, +_]](fg: F ~>> G, gf: G ~>> F): Mutex2[G] = new Mutex2[G] {
      override def bracket[E, A](f: G[E, A]): G[E, A] = fg(self.bracket(Morphism2.Ops[G, F](gf)(f)))
      override def bracket_[E, A](f: G[E, A]): G[E, Unit] = fg(self.bracket_(Morphism2.Ops[G, F](gf)(f)))
    }
  }
}
