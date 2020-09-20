package izumi.distage.effect.modules

import cats.Parallel
import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import izumi.distage.model.definition.ModuleDef
import monix.eval.Task
import monix.execution.Scheduler

/** `monix.eval.Task` effect type support for `distage` resources, effects, roles & tests
  *
  * - Adds [[cats.effect]] typeclass instances for `monix`
  * - Adds [[izumi.distage.model.effect.DIEffect]] instances to support using `monix` in `Injector`, `distage-framework` & `distage-testkit-scalatest`
  *
  * @param s is a [[monix.execution.Scheduler Scheduler]] that needs to be available in scope
  */
class MonixDIEffectModule(
  implicit s: Scheduler = Scheduler.global,
  opts: Task.Options = Task.defaultOptions,
) extends ModuleDef {
  include(PolymorphicCatsDIEffectModule[Task])
  include(PolymorphicCatsTypeclassesModule[Task])

  make[ConcurrentEffect[Task]].from(Task.catsEffect)

  addImplicit[ContextShift[Task]]
  addImplicit[Parallel[Task]]
  addImplicit[Timer[Task]]
}

object MonixDIEffectModule {
  /** @param s is a [[monix.execution.Scheduler Scheduler]] that needs to be available in scope */
  def apply(
    implicit s: Scheduler = Scheduler.global,
    opts: Task.Options = Task.defaultOptions,
  ): MonixDIEffectModule = new MonixDIEffectModule
}
