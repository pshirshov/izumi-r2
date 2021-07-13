package izumi.distage.modules.typeclass

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio._
import izumi.reflect.TagKK

/**
  * Adds `bio` typeclass instances for any effect type `F[+_, +_]` with an available `make[Async2[F]` binding
  *
  * Depends on `Async2[F]`
  *
  * @see [[izumi.functional.bio]]
  */
class BIO2InstancesModule[F[+_, +_]: TagKK] extends ModuleDef {
  make[Functor2[F]].using[Async2[F]]
  make[Bifunctor2[F]].using[Async2[F]]
  make[Applicative2[F]].using[Async2[F]]
  make[Guarantee2[F]].using[Async2[F]]
  make[ApplicativeError2[F]].using[Async2[F]]
  make[Monad2[F]].using[Async2[F]]
  make[Error2[F]].using[Async2[F]]
  make[Bracket2[F]].using[Async2[F]]
  make[Panic2[F]].using[Async2[F]]
  make[IO2[F]].using[Async2[F]]
  make[Parallel2[F]].using[Async2[F]]
  make[Concurrent2[F]].using[Async2[F]]
}

object BIO2InstancesModule {
  @inline def apply[F[+_, +_]: TagKK]: BIO2InstancesModule[F] = new BIO2InstancesModule

  /**
    * Make [[BIO2InstancesModule]], binding the required dependencies in place to values from implicit scope
    *
    * `make[Temporal2[F]]`, `make[UnsafeRun2[F]]` `make[Fork2[F]]` and `make[Primitives2[F]]` are not required by [[BIO2InstancesModule]]
    * but are added for completeness
    */
  def withImplicits[F[+_, +_]: TagKK: Async2: Temporal2: UnsafeRun2: Fork2: Primitives2: PrimitivesM2]: ModuleDef = new ModuleDef {
    include(BIO2InstancesModule[F])

    addImplicit[Async2[F]]
    addImplicit[Fork2[F]]
    addImplicit[Temporal2[F]]
    addImplicit[Primitives2[F]]
    addImplicit[PrimitivesM2[F]]
    addImplicit[UnsafeRun2[F]]
  }
}
