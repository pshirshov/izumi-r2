package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ModuleDef}
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, FinalPlan}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks

trait PlanningHook {
  def hookWiring(binding: Binding.ImplBinding, wiring: RuntimeDIUniverse.Wiring): RuntimeDIUniverse.Wiring = {
    Quirks.discard(binding)
    wiring
  }

  def hookDefinition(defn: ModuleDef): ModuleDef = defn

  def hookStep(context: ModuleDef, currentPlan: DodgyPlan, binding: Binding, next: DodgyPlan): DodgyPlan = {
    Quirks.discard(context, currentPlan, binding)
    next
  }

  def hookFinal(plan: FinalPlan): FinalPlan = plan
}
