package izumi.functional.bio

import cats.data.Kleisli
import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}
import izumi.functional.bio.PredefinedHelper.{NotPredefined, Predefined}
import izumi.functional.bio.SpecificityHelper._
import izumi.functional.bio.impl.{AsyncMonix, AsyncZio, BioEither}
import izumi.fundamentals.platform.language.unused
import zio.ZIO

import scala.language.implicitConversions
import izumi.fundamentals.orphans.`monix.bio.IO`

trait Root extends DivergenceHelper with PredefinedHelper

trait RootBifunctor[F[-_, +_, +_]] extends Root

trait RootTrifunctor[F[-_, +_, +_]] extends Root

object Root extends RootInstancesLowPriority1 {
  @inline implicit final def ConvertFromConcurrent[FR[-_, +_, +_]](implicit Concurrent: NotPredefined.Of[Concurrent3[FR]]): Panic3[FR] with S1 =
    S1(Concurrent.InnerF)

  @inline implicit final def AttachLocal[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Local: Local3[FR]): Local.type = Local
  @inline implicit final def AttachPrimitives3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Primitives: Primitives3[FR]): Primitives.type =
    Primitives
  @inline implicit final def AttachPrimitivesM3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit PrimitivesM: PrimitivesM3[FR]): PrimitivesM.type =
    PrimitivesM
  @inline implicit final def AttachFork3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Fork: Fork3[FR]): Fork.type = Fork
  @inline implicit final def AttachBlockingIO3[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit BlockingIO: BlockingIO3[FR]): BlockingIO.type = BlockingIO

  implicit final class KleisliSyntaxAttached[FR[-_, +_, +_]](private val FR0: Functor3[FR]) extends AnyVal {
    @inline def fromKleisli[R, E, A](k: Kleisli[FR[Any, E, _], R, A])(implicit FR: MonadAsk3[FR]): FR[R, E, A] = FR.fromKleisli(k)
    @inline def toKleisli[R, E, A](fr: FR[R, E, A])(implicit FR: Local3[FR]): Kleisli[FR[Any, E, _], R, A] = {
      val _ = FR0
      FR.toKleisli(fr)
    }
  }
}

sealed trait RootInstancesLowPriority1 extends RootInstancesLowPriority2 {
  @inline implicit final def ConvertFromTemporal[FR[-_, +_, +_]](implicit Temporal: NotPredefined.Of[Temporal3[FR]]): Error3[FR] with S2 =
    S2(Temporal.InnerF)

  @inline implicit final def AttachArrowChoice[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit ArrowChoice: ArrowChoice3[FR]): ArrowChoice.type =
    ArrowChoice
  @inline implicit final def AttachMonadAsk[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit MonadAsk: MonadAsk3[FR]): MonadAsk.type = MonadAsk
  @inline implicit final def AttachConcurrent[FR[-_, +_, +_], R](@unused self: Concurrent3[FR])(implicit Concurrent: Concurrent3[FR]): Concurrent.type =
    Concurrent
}

sealed trait RootInstancesLowPriority2 extends RootInstancesLowPriority3 {
  @inline implicit final def ConvertFromParallel[FR[-_, +_, +_]](implicit Parallel: NotPredefined.Of[Parallel3[FR]]): Monad3[FR] with S3 =
    S3(Parallel.InnerF)

  @inline implicit final def AttachArrow[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Arrow: Arrow3[FR]): Arrow.type = Arrow
  @inline implicit final def AttachBifunctor[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Bifunctor: Bifunctor3[FR]): Bifunctor.type =
    Bifunctor
  @inline implicit final def AttachConcurrent[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Concurrent: Concurrent3[FR]): Concurrent.type =
    Concurrent
}

sealed trait RootInstancesLowPriority3 extends RootInstancesLowPriority4 {
  @inline implicit final def ConvertFromMonadAsk[FR[-_, +_, +_]](implicit MonadAsk: NotPredefined.Of[MonadAsk3[FR]]): Monad3[FR] with S4 =
    S4(MonadAsk.InnerF)

  @inline implicit final def AttachAsk[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Ask: Ask3[FR]): Ask.type = Ask
  @inline implicit final def AttachProfunctor[FR[-_, +_, +_]](@unused self: Functor3[FR])(implicit Profunctor: Profunctor3[FR]): Profunctor.type =
    Profunctor
  @inline implicit final def AttachParallel[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Parallel: Parallel3[FR]): Parallel.type = Parallel
}

sealed trait RootInstancesLowPriority4 extends RootInstancesLowPriority5 {
  @inline implicit final def ConvertFromAsk[FR[-_, +_, +_]](implicit Ask: NotPredefined.Of[Ask3[FR]]): Applicative3[FR] with S5 = S5(Ask.InnerF)

  @inline implicit final def AttachTemporal[FR[-_, +_, +_], R](@unused self: Functor3[FR])(implicit Temporal: Temporal3[FR]): Temporal.type = Temporal
}

sealed trait RootInstancesLowPriority5 extends RootInstancesLowPriority6 {
  @inline implicit final def ConvertFromProfunctor[FR[-_, +_, +_]](implicit Profunctor: NotPredefined.Of[Profunctor3[FR]]): Functor3[FR] with S6 =
    S6(Profunctor.InnerF)
}

sealed trait RootInstancesLowPriority6 extends RootInstancesLowPriority7 {
  @inline implicit final def ConvertFromBifunctor[FR[-_, +_, +_]](implicit Bifunctor: NotPredefined.Of[Bifunctor3[FR]]): Functor3[FR] with S7 =
    S7(Bifunctor.InnerF)
}

sealed trait RootInstancesLowPriority7 extends RootInstancesLowPriority8 {
  @inline implicit final def Local3ZIO: Predefined.Of[Local3[ZIO]] = Predefined(AsyncZio)
  @inline implicit final def BIOZIO: Predefined.Of[Async3[ZIO]] = Predefined(AsyncZio)
}

sealed trait RootInstancesLowPriority8 extends RootInstancesLowPriority9 {
  /**
    * This instance uses 'no more orphans' trick to provide an Optional instance
    * only IFF you have monix-bio as a dependency without REQUIRING a monix-bio dependency.
    *
    * Optional instance via https://blog.7mind.io/no-more-orphans.html
    */
  // for some reason ZIO instances do not require no-more-orphans machinery and do not create errors when zio is not on classpath...
  // seems like it's because of the type lambda in `Async2` definition
  @inline implicit final def BIOMonix[MonixBIO[+_, +_]](implicit @unused M: `monix.bio.IO`[MonixBIO]): Predefined.Of[Async2[MonixBIO]] =
    AsyncMonix.asInstanceOf[Predefined.Of[Async2[MonixBIO]]]
}

sealed trait RootInstancesLowPriority9 extends RootInstancesLowPriority10 {
  @inline implicit final def BIOEither: Predefined.Of[Error2[Either]] = Predefined(BioEither)
}

sealed trait RootInstancesLowPriority10 {
  @inline implicit final def Convert3To2[C[f[-_, +_, +_]] <: DivergenceHelper with RootBifunctor[f], FR[-_, +_, +_], R0](
    implicit BifunctorPlus: C[FR] { type Divergence = Nondivergent }
  ): C[Lambda[(`-R`, `+E`, `+A`) => FR[R0, E, A]]] with DivergenceHelper { type Divergence = Divergent } =
    Divergent(cast3To2[C, FR, R0](BifunctorPlus))
}
