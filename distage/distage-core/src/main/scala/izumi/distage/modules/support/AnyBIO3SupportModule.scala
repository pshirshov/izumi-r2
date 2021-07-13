package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.effect._
import izumi.distage.modules.typeclass.BIO3InstancesModule
import izumi.functional.bio.{Async2, Async3, Fork2, Fork3, Local3, Primitives2, Primitives3, PrimitivesM2, PrimitivesM3, SyncSafe2, SyncSafe3, Temporal2, Temporal3, UnsafeRun2, UnsafeRun3}
import izumi.reflect.{TagK3, TagKK}

import scala.annotation.unchecked.{uncheckedVariance => v}

/**
  * Any `BIO` effect type support for `distage` resources, effects, roles & tests.
  *
  * For all `F[-_, +_, +_]` with available `make[Async3[F]]`, `make[Temporal3[F]]` and `make[UnsafeRun3[F]]` bindings.
  *
  *  - Adds [[izumi.distage.model.effect.QuasiIO]] instances to support using `F[-_, +_, +_]` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *  - Adds [[izumi.functional.bio]] typeclass instances for `F[-_, +_, +_]`
  *
  * Depends on `make[Async3[F]]`, `make[Temporal3[F]]`, `make[Local3[F]]`, `make[Fork3[F]]` & `make[UnsafeRun3[F]]`
  */
class AnyBIO3SupportModule[F[-_, +_, +_]: TagK3](implicit tagBIO: TagKK[F[Any, +_, +_]]) extends ModuleDef {
  // QuasiIO & bifunctor bio instances
  include(AnyBIO2SupportModule[F[Any, +_, +_]])
  // trifunctor bio instances
  include(BIO3InstancesModule[F])
  addConverted3To2[F[Any, +_, +_]]

  // workaround for
  // - https://github.com/zio/izumi-reflect/issues/82
  // - https://github.com/zio/izumi-reflect/issues/83
  def addConverted3To2[G[+e, +a] >: F[Any, e @v, a @v] <: F[Any, e @v, a @v]: TagKK]: Unit = {
    make[Async2[G]].from {
      implicit F: Async3[F] => Async2[F[Any, +_, +_]]
    }
    make[Temporal2[G]].from {
      implicit F: Temporal3[F] => Temporal2[F[Any, +_, +_]]
    }
    make[Fork2[G]].from {
      implicit Fork: Fork3[F] => Fork2[F[Any, +_, +_]]
    }
    ()
  }
}

object AnyBIO3SupportModule extends App with ModuleDef {
  @inline def apply[F[-_, +_, +_]: TagK3](implicit tagBIO: TagKK[F[Any, +_, +_]]): AnyBIO3SupportModule[F] = new AnyBIO3SupportModule

  /**
    * Make [[AnyBIO3SupportModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Fork3[F]]` and `make[Primitives3[F]]` are not required by [[AnyBIO3SupportModule]]
    * but are added for completeness
    */
  def withImplicits[F[-_, +_, +_]: TagK3: Async3: Temporal3: Local3: UnsafeRun3: Fork3: Primitives3: PrimitivesM3](implicit tagBIO: TagKK[F[Any, +_, +_]]): ModuleDef =
    new ModuleDef {
      include(AnyBIO3SupportModule[F])

      addImplicit[Async3[F]]
      addImplicit[Temporal3[F]]
      addImplicit[Local3[F]]
      addImplicit[Fork3[F]]
      addImplicit[Primitives3[F]]
      addImplicit[PrimitivesM3[F]]
      addImplicit[UnsafeRun3[F]]

      // no corresponding bifunctor (`F[Any, +_, +_]`) instances need to be added for these types because they already match
      private[this] def aliasingCheck(): Unit = {
        lazy val _ = aliasingCheck()
        implicitly[UnsafeRun3[F] =:= UnsafeRun2[F[Any, +_, +_]]]
        implicitly[Primitives3[F] =:= Primitives2[F[Any, +_, +_]]]
        implicitly[PrimitivesM3[F] =:= PrimitivesM2[F[Any, +_, +_]]]
        implicitly[SyncSafe3[F] =:= SyncSafe2[F[Any, +_, +_]]]
        implicitly[QuasiIORunner3[F] =:= QuasiIORunner2[F[Any, +_, +_]]]
        implicitly[QuasiIO3[F] =:= QuasiIO2[F[Any, +_, +_]]]
        implicitly[QuasiApplicative3[F] =:= QuasiApplicative2[F[Any, +_, +_]]]
        implicitly[QuasiAsync3[F] =:= QuasiAsync2[F[Any, +_, +_]]]
        ()
      }
    }
}
