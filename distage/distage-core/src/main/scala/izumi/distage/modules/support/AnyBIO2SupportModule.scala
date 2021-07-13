package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect._
import izumi.distage.modules.typeclass.BIO2InstancesModule
import izumi.functional.bio.{Applicative2, Async2, Clock2, Entropy2, Fork2, IO2, Primitives2, PrimitivesM2, SyncSafe2, Temporal2, UnsafeRun2}
import izumi.functional.mono.{Clock, Entropy, SyncSafe}
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagKK

/**
  * Any `BIO` effect type support for `distage` resources, effects, roles & tests.
  *
  * For all `F[+_, +_]` with available `make[Async2[F]]`, `make[Temporal2[F]]` and `make[UnsafeRun2[F]]` bindings.
  *
  *  - Adds [[izumi.distage.model.effect.QuasiIO]] instances to support using `F[+_, +_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds [[izumi.functional.bio]] typeclass instances for `F[+_, +_]`
  *
  * Depends on `make[Async2[F]]`, `make[Temporal2[F]]`, `make[UnsafeRun2[F]]`
  */
class AnyBIO2SupportModule[F[+_, +_]: TagKK] extends ModuleDef {
  include(BIO2InstancesModule[F])

  make[QuasiIORunner2[F]]
    .from[QuasiIORunner.BIOImpl[F]]

  make[QuasiIO2[F]].from {
    QuasiIO.fromBIO(_: IO2[F])
  }
  make[QuasiApplicative2[F]].from {
    QuasiApplicative.fromBIO[F, Throwable](_: Applicative2[F])
  }
  make[QuasiAsync2[F]].from {
    QuasiAsync.fromBIO(_: Async2[F], _: Temporal2[F])
  }
  make[SyncSafe2[F]].from {
    SyncSafe.fromBIO(_: IO2[F])
  }
  make[SyncSafe[F[Throwable, _]]].from((_: SyncSafe2[F]).widen[F[Throwable, _]])
  make[Clock2[F]].aliased[Clock[F[Throwable, _]]].from {
    Clock.fromImpure(_: Clock[Identity])(_: SyncSafe2[F])
  }
  make[Entropy2[F]].aliased[Entropy[F[Throwable, _]]].from {
    Entropy.fromImpure(_: Entropy[Identity])(_: SyncSafe2[F])
  }
}

object AnyBIO2SupportModule extends ModuleDef {
  @inline def apply[F[+_, +_]: TagKK]: AnyBIO2SupportModule[F] = new AnyBIO2SupportModule

  /**
    * Make [[AnyBIO2SupportModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Fork2[F]]` and `make[Primitives2[F]]` are not required by [[AnyBIO2SupportModule]]
    * but are added for completeness
    */
  def withImplicits[F[+_, +_]: TagKK: Async2: Temporal2: UnsafeRun2: Fork2: Primitives2: PrimitivesM2]: ModuleDef = new ModuleDef {
    include(AnyBIO2SupportModule[F])

    addImplicit[Async2[F]]
    addImplicit[Fork2[F]]
    addImplicit[Temporal2[F]]
    addImplicit[Primitives2[F]]
    addImplicit[PrimitivesM2[F]]
    addImplicit[UnsafeRun2[F]]
  }
}
