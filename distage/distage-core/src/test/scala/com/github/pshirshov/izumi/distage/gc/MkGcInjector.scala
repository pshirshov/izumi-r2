package com.github.pshirshov.izumi.distage.gc

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.plan.OrderedPlan
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.planning.AutoSetModule
import distage.Injector

trait MkGcInjector {
  def mkInjector(roots: RuntimeDIUniverse.DIKey*): Injector = {
    Injector.gc(roots.toSet).apply(AutoSetModule().register[AutoCloseable])
  }

  implicit class InjectorExt(private val injector: Injector) {
    def fproduce(plan: OrderedPlan): Locator = {
      injector.produce(injector.finish(plan.toSemi))
    }
  }
}
