package izumi.distage.modules.typeclass

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio._
import izumi.reflect.TagK3

/**
  * Adds `bio` typeclass instances for any effect type `F[-_, +_, +_]` with an available `make[Async3[F]` binding
  *
  * Depends on `Async3[F]` and `Local3[F]`
  *
  * @note doesn't add bifunctor variants from [[BIO2InstancesModule]]
  *
  * @see [[izumi.functional.bio]]
  */
class BIO3InstancesModule[F[-_, +_, +_]: TagK3] extends ModuleDef {
  make[Functor3[F]].using[Async3[F]]
  make[Bifunctor3[F]].using[Async3[F]]
  make[Applicative3[F]].using[Async3[F]]
  make[Guarantee3[F]].using[Async3[F]]
  make[ApplicativeError3[F]].using[Async3[F]]
  make[Monad3[F]].using[Async3[F]]
  make[Error3[F]].using[Async3[F]]
  make[Bracket3[F]].using[Async3[F]]
  make[Panic3[F]].using[Async3[F]]
  make[IO3[F]].using[Async3[F]]
  make[Parallel3[F]].using[Async3[F]]
  make[Concurrent3[F]].using[Async3[F]]

  make[Ask3[F]].using[Local3[F]]
  make[MonadAsk3[F]].using[Local3[F]]
  make[Profunctor3[F]].using[Local3[F]]
  make[Arrow3[F]].using[Local3[F]]
}

object BIO3InstancesModule {
  @inline def apply[F[-_, +_, +_]: TagK3]: BIO3InstancesModule[F] = new BIO3InstancesModule

  /**
    * Make [[BIO3InstancesModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Temporal3[F]]`, `make[UnsafeRun3[F]]` `make[Fork3[F]]` and `make[Primitives3[F]]` are not required by [[BIO3InstancesModule]]
    * but are added for completeness
    */
  def withImplicits[F[-_, +_, +_]: TagK3: Async3: Temporal3: Local3: UnsafeRun3: Fork3: Primitives3: PrimitivesM3]: ModuleDef = new ModuleDef {
    include(BIO3InstancesModule[F])

    addImplicit[Async3[F]]
    addImplicit[Temporal3[F]]
    addImplicit[Local3[F]]
    addImplicit[Fork3[F]]
    addImplicit[Primitives3[F]]
    addImplicit[PrimitivesM3[F]]
    addImplicit[UnsafeRun3[F]]
  }
}
