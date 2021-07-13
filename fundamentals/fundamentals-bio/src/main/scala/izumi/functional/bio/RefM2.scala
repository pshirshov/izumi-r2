package izumi.functional.bio

import izumi.functional.bio.data.{Morphism2, ~>>}
import zio.{IO, RefM}

trait RefM2[F[+_, +_], A] {
  def get: F[Nothing, A]
  def set(a: A): F[Nothing, Unit]

  def modify[E, B](f: A => F[E, (B, A)]): F[E, B]
  def update[E](f: A => F[E, A]): F[E, A]
  def update_[E](f: A => F[E, A]): F[E, Unit]
}

object RefM2 {
  def fromZIO[A](ref: RefM[A]): RefM2[IO, A] =
    new RefM2[IO, A] {
      override def get: IO[Nothing, A] = ref.get
      override def set(a: A): IO[Nothing, Unit] = ref.set(a)
      override def modify[E, B](f: A => IO[E, (B, A)]): IO[E, B] = ref.modify(f)
      override def update[E](f: A => IO[E, A]): IO[E, A] = ref.updateAndGet(f)
      override def update_[E](f: A => IO[E, A]): IO[E, Unit] = ref.update(f)
    }

  def createFromBIO[F[+_, +_]: Bracket2: Primitives2, A](a: A): F[Nothing, RefM2[F, A]] = {
    for {
      mutex <- Mutex2.createFromBIO[F]
      ref   <- F.mkRef(a)
    } yield {
      new RefM2[F, A] {
        override def get: F[Nothing, A] = ref.get

        override def set(a: A): F[Nothing, Unit] = mutex.bracket(ref.set(a))

        override def modify[E, B](f: A => F[E, (B, A)]): F[E, B] = mutex.bracket {
          for {
            a0    <- ref.get
            res   <- f(a0)
            (b, a) = res
            _     <- ref.set(a)
          } yield b
        }

        override def update[E](f: A => F[E, A]): F[E, A] = mutex.bracket {
          for {
            a0 <- ref.get
            a  <- f(a0)
            _  <- ref.set(a)
          } yield a
        }

        override def update_[E](f: A => F[E, A]): F[E, Unit] = update(f).void
      }
    }
  }

  implicit final class RefM2Ops[F[+_, +_], A](private val self: RefM2[F, A]) extends AnyVal {
    def mapK[G[+_, +_]](fg: F ~>> G, gf: G ~>> F): RefM2[G, A] = new RefM2[G, A] {
      override def get: G[Nothing, A] = fg(self.get)
      override def set(a: A): G[Nothing, Unit] = fg(self.set(a))
      override def modify[E, B](f: A => G[E, (B, A)]): G[E, B] = fg(self.modify(a => Morphism2.Ops[G, F](gf)(f(a))))
      override def update[E](f: A => G[E, A]): G[E, A] = fg(self.update(a => Morphism2.Ops[G, F](gf)(f(a))))
      override def update_[E](f: A => G[E, A]): G[E, Unit] = fg(self.update_(a => Morphism2.Ops[G, F](gf)(f(a))))
    }
  }
}
