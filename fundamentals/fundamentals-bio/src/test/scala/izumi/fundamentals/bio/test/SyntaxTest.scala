package izumi.fundamentals.bio.test

import monix.bio
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class SyntaxTest extends AnyWordSpec {

  implicit val clock: zio.clock.Clock = zio.Has(zio.clock.Clock.Service.live)

  "BIOParallel.zipPar/zipParLeft/zipParRight/zipWithPar is callable" in {
    import izumi.functional.bio.Parallel2

    def x[F[+_, +_]: Parallel2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b)
    }

    x[zio.IO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "BIOParallel3.zipPar/zipParLeft/zipParRight/zipWithPar is callable" in {
    import izumi.functional.bio.Parallel3

    def x[F[-_, +_, +_]: Parallel3](a: F[Any, Nothing, Unit], b: F[Any, Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b)
    }

    x[zio.ZIO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "BIOConcurrent syntax / attachment / conversion works" in {
    import izumi.functional.bio.{Concurrent2, F}

    def x[F[+_, +_]: Concurrent2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b)
      a.guaranteeCase(_ => a.race(b).widenError[Throwable].catchAll(_ => F.unit orElse F.uninterruptible(F.race(a, b))).void)
      F.unit
    }

    x[zio.IO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "BIOConcurrent3 syntax / attachment / conversion works" in {
    import izumi.functional.bio.{Concurrent3, F}

    def x[F[-_, +_, +_]: Concurrent3](a: F[Any, Nothing, Unit], b: F[Any, Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.flatMap(_ => b)
      a.guaranteeCase(_ => a.race(b).widenError[Throwable].catchAll(_ => F.unit orElse F.uninterruptible(F.race(a, b))).void)
      F.unit
    }

    x[zio.ZIO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "Async2.race is callable along with all BIOParallel syntax" in {
    import izumi.functional.bio.Async2

    def x[F[+_, +_]: Async2](a: F[Nothing, Unit], b: F[Nothing, Unit]) = {
      a zipPar b
      a zipParLeft b
      a zipParRight b
      a.zipWithPar(b)((a, b) => (a, b))
      a.race(b)
      a.flatMap(_ => b)
    }

    x[zio.IO](zio.UIO.succeed(()), zio.UIO.succeed(()))
    x[bio.IO](bio.UIO.evalTotal(()), bio.UIO.evalTotal(()))
  }

  "Async3.race is callable along with all BIOParallel syntax" in {
    import izumi.functional.bio.Async3

    def x[F[-_, +_, +_]: Async3](a: F[Any, Nothing, Unit], b: F[Any, Nothing, Unit]) = {
      a.zipPar(b)
      a.zipParLeft(b)
      a.zipParRight(b)
      a.zipWithPar(b)((a, b) => (a, b))
      a.race(b)
      a.flatMap(_ => b)
    }

    x[zio.ZIO](zio.UIO.succeed(()), zio.UIO.succeed(()))
  }

  "IO2.apply is callable" in {
    import izumi.functional.bio.IO2

    class X[F[+_, +_]: IO2] {
      def hello = IO2(println("hello world!"))
    }

    assert(new X[zio.IO].hello != null)
    assert(new X[bio.IO].hello != null)
  }

  ".widen/widenError is callable" in {
    import izumi.functional.bio.{F, IO2}

    def x[F[+_, +_]: IO2]: F[Throwable, AnyVal] = {
      identity[F[Throwable, AnyVal]] {
        F.pure(None: Option[Int]).flatMap {
          _.fold(
            F.unit.widenError[Throwable].widen[AnyVal]
          )(_ => F.fail(new RuntimeException))
        }
      }
    }

    x[zio.IO]
  }

  "Bracket2.bracketCase & guaranteeCase are callable" in {
    import izumi.functional.bio.{Bracket2, Exit, F}

    def x[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(None).bracketCase(release = {
          (_, _: Exit[Throwable, Int]) => F.unit
        })(_ => F.pure(1))
    }
    def y[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(None).bracketCase {
          (_, exit: Exit[Throwable, Int]) =>
            exit match {
              case Exit.Success(x) => F.pure(x).as(())
              case _ => F.unit
            }
        }(_ => F.pure(1))
    }
    def z[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(1).guaranteeCase {
        case Exit.Success(x) => F.pure(x).as(())
        case _ => F.unit
      }
    }
    def zz[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.when(F.pure(false).widenError[Throwable])(F.unit).as(1).guaranteeCase {
          case Exit.Success(x) => F.pure(x).as(())
          case _ => F.unit
        }.widenError[Throwable]
    }

    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    zz[zio.IO]

    x[bio.IO]
    y[bio.IO]
    z[bio.IO]
    zz[bio.IO]
  }

  "Bracket3.bracketCase & guaranteeCase are callable" in {
    import izumi.functional.bio.{Bracket3, Exit, F}

    def x[F[-_, +_, +_]: Bracket3]: F[Any, Throwable, Int] = {
      F.pure(None).bracketCase(release = {
          (_, _: Exit[Throwable, Int]) => F.unit
        })(_ => F.pure(1))
    }
    def y[F[-_, +_, +_]: Bracket3]: F[Any, Throwable, Int] = {
      F.pure(None).bracketCase {
          (_, exit: Exit[Throwable, Int]) =>
            exit match {
              case Exit.Success(x) => F.pure(x).as(())
              case _ => F.unit
            }
        }(_ => F.pure(1))
    }
    def z[F[-_, +_, +_]: Bracket3]: F[Any, Throwable, Int] = {
      F.pure(1).guaranteeCase {
        case Exit.Success(x) => F.pure(x).as(())
        case _ => F.unit
      }
    }
    def zz[F[-_, +_, +_]: Bracket3]: F[Any, Throwable, Int] = {
      F.when(F.pure(false).widenError[Throwable])(F.unit).as(1).guaranteeCase {
          case Exit.Success(x) => F.pure(x).as(())
          case _ => F.unit
        }.widenError[Throwable]
    }

    x[zio.ZIO]
    y[zio.ZIO]
    z[zio.ZIO]
    zz[zio.ZIO]
  }

  "Bracket2.bracketOnFailure & guaranteeOnFailure are callable" in {
    import izumi.functional.bio.{Bracket2, Exit, F}

    def x[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(None).bracketOnFailure(cleanupOnFailure = {
          (_, _: Exit.Failure[Throwable]) => F.unit
        })(_ => F.pure(1))
    }
    def y[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(None).bracketOnFailure {
          (_, _: Exit.Failure[Throwable]) => F.unit
        }(_ => F.pure(1))
    }
    def z[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.pure(1).guaranteeOnFailure(_ => F.unit)
    }
    def zz[F[+_, +_]: Bracket2]: F[Throwable, Int] = {
      F.when(F.pure(false).widenError[Throwable])(F.unit).as(1).guaranteeOnFailure(_ => F.unit).widenError[Throwable]
    }

    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    zz[zio.IO]

    x[bio.IO]
    y[bio.IO]
    z[bio.IO]
    zz[bio.IO]
  }

  "Bracket3.bracketOnFailure & guaranteeOnFailure are callable" in {
    import izumi.functional.bio.{Bracket3, Exit, F}

    def x[F[-_, +_, +_]: Bracket3]: F[Any, Throwable, Int] = {
      F.pure(None).bracketOnFailure(cleanupOnFailure = {
          (_, _: Exit.Failure[Throwable]) => F.unit
        })(_ => F.pure(1))
    }
    def y[F[-_, +_, +_]: Bracket3]: F[Any, Throwable, Int] = {
      F.pure(None).bracketOnFailure {
          (_, _: Exit.Failure[Throwable]) => F.unit
        }(_ => F.pure(1))
    }
    def z[F[-_, +_, +_]: Bracket3]: F[Any, Throwable, Int] = {
      F.pure(1).guaranteeOnFailure(_ => F.unit)
    }
    def zz[F[-_, +_, +_]: Bracket3]: F[Any, Throwable, Int] = {
      F.when(F.pure(false).widenError[Throwable])(F.unit).as(1).guaranteeOnFailure(_ => F.unit).widenError[Throwable]
    }

    x[zio.ZIO]
    y[zio.ZIO]
    z[zio.ZIO]
    zz[zio.ZIO]
  }

  "BIO.when/unless/ifThenElse have nice inference" in {
    import izumi.functional.bio.{F, Monad2}

    def x[F[+_, +_]: Monad2] = {
      F.ifThenElse(F.pure(false): F[RuntimeException, Boolean])(F.pure(()), F.pure(()): F[Throwable, Any]) *>
      F.when(F.pure(false): F[RuntimeException, Boolean])(F.pure(()): F[Throwable, Unit])
    }

    x[zio.IO]
  }

  "withFilter test" in {
    import izumi.functional.bio.{Error2, F}

    def x[F[+_, +_]: Error2]: F[NoSuchElementException, Unit] = {
      assertDoesNotCompile(
        """
        for {
          (1, 2) <- F.pure(Option((2, 1)))
        } yield ()
        """
      )
      for {
        (1, 2) <- F.pure((2, 1))
      } yield ()
    }
    def y[F[+_, +_]: Error2]: F[Any, Unit] = {
      for {
        (1, 2) <- F.pure((2, 1))
      } yield ()
    }
    def z[F[+_, +_]: Error2]: F[String, Unit] = {
      for {
        (1, 2) <- F.pure((2, 1)).widenError[String]
      } yield ()
    }
    def xx[F[+_, +_]: Error2]: F[Unit, Unit] = {
      for {
        (1, 2) <- F.pure((2, 1)).widenError[Unit]
      } yield ()
    }
    def yy[F[+_, +_]: Error2]: F[Option[Throwable], Unit] = {
      for {
        (1, 2) <- F.pure((2, 1)).widenError[Option[Throwable]]
      } yield ()
    }
    x[zio.IO]
    y[zio.IO]
    z[zio.IO]
    xx[zio.IO]
    yy[zio.IO]
  }

  "F summoner examples" in {
    import izumi.functional.bio.{F, Fork2, Fork3, Functor2, Monad2, Monad3, Primitives2, Primitives3, Temporal2, PrimitivesM3}

    def x[F[+_, +_]: Monad2] = {
      F.when(false)(F.unit)
    }
    def y[F[+_, +_]: Temporal2: Fork2] = {
      F.timeout(5.seconds)(F.forever(F.unit)) *>
      F.map(z[F])(_ => ())
    }
    def z[F[+_, +_]: Functor2]: F[Nothing, Unit] = {
      F.map(z[F])(_ => ())
    }
    def `attach Primitives2 & Fork2 methods even when they aren't imported`[F[+_, +_]: Monad2: Primitives2: Fork2]: F[Nothing, Int] = {
      F.fork[Any, Nothing, Int] {
        F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1))
      }.flatMap(_.join) *>
      F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1)).fork.flatMap(_.join)
    }
    def `attach Primitives2 & Fork23 methods to a trifunctor BIO even when not imported`[FR[-_, +_, +_]: Monad3: Primitives3: Fork3]: FR[Nothing, Nothing, Int] = {
      F.fork(F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1))).flatMap(_.join) *>
      F.mkRef(4).flatMap(r => r.update(_ + 5) *> r.get.map(_ - 1)).fork.flatMap(_.join)
    }
    def `attach PrimitivesM2 methods to a trifunctor BIO even when not imported`[FR[-_, +_, +_]: Monad3: PrimitivesM3]: FR[Nothing, Nothing, Int] = {
      F.mkRefM(4).flatMap(r => r.update(_ => F.pure(5)) *> r.get.map(_ - 1)) *>
      F.mkMutex.flatMap(m => m.bracket(F.pure(10)))
    }
    lazy val zioTest = {
      (
        x[zio.IO],
        y[zio.IO],
        z[zio.IO],
        `attach Primitives2 & Fork2 methods even when they aren't imported`[zio.IO],
        `attach Primitives2 & Fork23 methods to a trifunctor BIO even when not imported`[zio.ZIO],
        `attach PrimitivesM2 methods to a trifunctor BIO even when not imported`[zio.ZIO],
      )
    }

    lazy val monixTest = (
      x[bio.IO],
      y[bio.IO],
      z[bio.IO],
    )

    lazy val eitherTest = (
      x[Either],
      z[Either],
    )
    lazy val _ = (zioTest, monixTest, eitherTest)
  }

  "Support BIO syntax for ZIO with wildcard import" in {
    import izumi.functional.bio._

    zio.IO.effectTotal(List(4)).flatMap {
      F.traverse(_)(_ => zio.IO.unit)
    } *> F.unit
    implicitly[Bifunctor3[zio.ZIO]]
    implicitly[Bifunctor2[zio.IO]]
  }

  "FR: Local/Ask summoners examples" in {
    import izumi.functional.bio.{Arrow3, Ask3, Bifunctor3, F, Local3, Monad3, MonadAsk3, Profunctor3, Temporal3}

    def x[FR[-_, +_, +_]: Monad3: Ask3] = {
      F.unit *> F.ask[Int].map {
        _: Int =>
          true
      } *>
      F.unit *> F.askWith {
        _: Int =>
          true
      }
    }
    def onlyMonadAsk[FR[-_, +_, +_]: MonadAsk3]: FR[Int, Nothing, Unit] = {
      F.unit <* F.askWith {
        _: Int =>
          true
      }
    }
    def onlyMonadAskAccess[FR[-_, +_, +_]: MonadAsk3]: FR[Int, Nothing, Unit] = {
      F.unit <* F.access {
        _: Int =>
          F.unit
      }
    }
    def onlyAsk[FR[-_, +_, +_]: Ask3]: FR[Int, Nothing, Unit] = {
      F.askWith {
        _: Int =>
          true
      } *> F.unit
    }
    def y[FR[-_, +_, +_]: Local3]: FR[Any, Throwable, Unit] = {
      F.fromKleisli {
        F.askWith {
          _: Int =>
            ()
        }.toKleisli
      }.provide(4)
    }
    def arrowAsk[FR[-_, +_, +_]: Arrow3: Ask3]: FR[String, Throwable, Int] = {
      F.askWith {
        _: Int =>
          ()
      }.dimap {
          _: String =>
            4
        }(_ => 1)
    }
    def profunctorOnly[FR[-_, +_, +_]: Profunctor3]: FR[String, Throwable, Int] = {
      F.contramap(??? : FR[Unit, Throwable, Int]) {
        _: Int =>
          ()
      }.dimap {
          _: String =>
            4
        }(_ => 1)
        .map(_ + 2)
    }
    def bifunctorOnly[FR[-_, +_, +_]: Bifunctor3]: FR[Unit, Int, Int] = {
      F.leftMap(??? : FR[Unit, Int, Int]) {
        _: Int =>
          ()
      }.bimap(
          {
            _: Unit =>
              4
          },
          _ => 1,
        )
        .map(_ + 2)
    }
    def Temporal2PlusLocal[FR[-_, +_, +_]: Temporal3: Local3]: FR[Any, Throwable, Unit] = {
      F.fromKleisli {
        F.askWith {
          _: Int =>
            ()
        }.toKleisli
      }.provide(4).flatMap(_ => F.unit).widenError[Throwable].leftMap(identity)
    }
    def biomonadPlusLocal[FR[-_, +_, +_]: Monad3: Bifunctor3: Local3]: FR[Any, Throwable, Unit] = {
      F.fromKleisli {
        F.askWith {
          _: Int =>
            ()
        }.toKleisli
      }.provide(4).flatMap(_ => F.unit).widenError[Throwable].leftMap(identity)
    }

    lazy val _ = (
      x[zio.ZIO],
      onlyMonadAsk[zio.ZIO],
      onlyMonadAskAccess[zio.ZIO],
      onlyAsk[zio.ZIO],
      y[zio.ZIO],
      arrowAsk[zio.ZIO],
      profunctorOnly[zio.ZIO],
      Temporal2PlusLocal[zio.ZIO],
      biomonadPlusLocal[zio.ZIO],
      bifunctorOnly[zio.ZIO],
    )
  }

  "doc examples" in {
    locally {
      import izumi.functional.bio.{F, Monad2, Primitives2}
      def adder[F[+_, +_]: Monad2: Primitives2](i: Int): F[Nothing, Int] =
        F.mkRef(0)
          .flatMap(ref => ref.update(_ + i) *> ref.get)

      lazy val _ = adder[zio.IO](1)
    }

    locally {
      import izumi.functional.bio.{F, MonadAsk3, Ref3}
      // update ref from the environment and return result
      def adderEnv[F[-_, +_, +_]: MonadAsk3](i: Int): F[Ref3[F, Int], Nothing, Int] =
        F.access {
          ref =>
            for {
              _ <- ref.update(_ + i)
              res <- ref.get
            } yield res
        }

      lazy val _ = adderEnv[zio.ZIO](1)
    }

    locally {
      import izumi.functional.bio.{F, Temporal2}

      def y[F[+_, +_]: Temporal2] = {
        F.timeout(5.seconds)(F.forever(F.unit))
      }

      lazy val _ = (
        y[zio.IO],
        y[monix.bio.IO],
      )
    }
  }

  "BIO.iterateUntil/iterateWhile are callable" in {
    import izumi.functional.bio.{Error2, Monad2}

    def x[F[+_, +_]: Monad2](a: F[Nothing, Unit]) = {
      a.iterateWhile(_ => true)
      a.iterateUntil(_ => false)
    }

    def y[F[+_, +_]: Error2](a: F[Nothing, Unit]) = {
      a.iterateWhile(_ => true)
      a.iterateUntil(_ => false)
    }

    x[zio.IO](zio.UIO.succeed(()))
    y[zio.IO](zio.UIO.succeed(()))
  }

  "BIO3.iterateUntil/iterateWhile are callable" in {
    import izumi.functional.bio.{Error3, Monad3}

    def x[F[-_, +_, +_]: Monad3](a: F[Any, Nothing, Unit]) = {
      a.iterateWhile(_ => true)
      a.iterateUntil(_ => false)
    }

    def y[F[-_, +_, +_]: Error3](a: F[Any, Nothing, Unit]) = {
      a.iterateWhile(_ => true)
      a.iterateUntil(_ => false)
    }

    x[zio.ZIO](zio.UIO.succeed(()))
    y[zio.ZIO](zio.UIO.succeed(()))
  }

  "BIO.retryUntil/retryUntilF/retryWhile/retryWhileF/fromOptionOr/fromOptionF/fromOption are callable" in {
    import izumi.functional.bio.{Error2, F, Functor2, Monad2}

    def x[F[+_, +_]: Functor2](aOpt: F[String, Option[Unit]]) = {
      aOpt.fromOptionOr(())
    }

    def y[F[+_, +_]: Monad2](aOpt: F[String, Option[Unit]]) = {
      aOpt.fromOptionOr(())
      aOpt.fromOptionF(F.unit)
    }

    def z[F[+_, +_]: Error2](a: F[String, Unit], aOpt: F[String, Option[Unit]]) = {
      a.retryUntil(_ => true)
      a.retryUntilF(_ => F.pure(false))
      a.retryWhile(_ => false)
      a.retryWhileF(_ => F.pure(true))
      aOpt.fromOptionOr(())
      aOpt.fromOptionF(F.unit)
      aOpt.fromOption("ooops")
    }

    x[zio.IO](zio.UIO.succeed(Option(())))
    y[zio.IO](zio.UIO.succeed(Option(())))
    z[zio.IO](zio.UIO.succeed(()), zio.UIO.succeed(Option(())))
  }

  "BIO3.retryUntil/retryUntilF/retryWhile/retryWhileF/fromOptionOr/fromOptionF/fromOption are callable" in {
    import izumi.functional.bio.{Error3, F, Functor3, Monad3}

    def x[F[-_, +_, +_]: Functor3](aOpt: F[Any, String, Option[Unit]]) = {
      aOpt.fromOptionOr(())
    }

    def y[F[-_, +_, +_]: Monad3](aOpt: F[Any, String, Option[Unit]]) = {
      aOpt.fromOptionOr(())
      aOpt.fromOptionF(F.unit)
    }

    def z[F[-_, +_, +_]: Error3](a: F[Any, String, Unit], aOpt: F[Any, String, Option[Unit]]) = {
      a.retryUntil(_ => true)
      a.retryUntilF(_ => F.pure(false))
      a.retryWhile(_ => false)
      a.retryWhileF(_ => F.pure(true))
      aOpt.fromOptionOr(())
      aOpt.fromOptionF(F.unit)
      aOpt.fromOption("ooops")
    }

    x[zio.ZIO](zio.UIO.succeed(Option(())))
    y[zio.ZIO](zio.UIO.succeed(Option(())))
    z[zio.ZIO](zio.UIO.succeed(()), zio.UIO.succeed(Option(())))
  }

  "Fiber#toCats syntax works" in {
    import izumi.functional.bio.{Applicative2, Applicative3, F, Fork2, Fork3}

    def x2[F[+_, +_]: Applicative2: Fork2] = {
      F.unit.fork.map(_.toCats)
    }
    def x3[F[-_, +_, +_]: Applicative3: Fork3] = {
      F.unit.fork.map(_.toCats)
    }
    x2[zio.IO]
    x3[zio.ZIO]
  }

  "Fiber3 and Fiber2 types are wholly compatible" in {
    import izumi.functional.bio.{Applicative2, Applicative3, F, Fiber2, Fiber3, Fork2, Fork3}

    def x2[FR[-_, +_, +_]](implicit applicative: Applicative2[FR[Any, +_, +_]], fork: Fork2[FR[Any, +_, +_]]) = {
      type F[+E, +A] = FR[Any, E, A]
      for {
        fiber <- F.unit.fork
      } yield {
        val fiber2: Fiber2[F, Nothing, Unit] = fiber
        val fiber3: Fiber3[FR, Nothing, Unit] = fiber
        (fiber2, fiber3)
      }
    }
    def x3[FR[-_, +_, +_]: Applicative3: Fork3] = {
      type F[+E, +A] = FR[Any, E, A]
      for {
        fiber <- F.unit.fork
      } yield {
        val fiber2: Fiber2[F, Nothing, Unit] = fiber
        val fiber3: Fiber3[FR, Nothing, Unit] = fiber
        (fiber2, fiber3)
      }
    }

    x2[zio.ZIO]
    x3[zio.ZIO]
  }
}
