package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.distage.model.plan.FinalPlan
import com.github.pshirshov.izumi.distage.model.{Injector, Locator, Planner, TheFactoryOfAllTheFactories}


class InjectorDefaultImpl(parentContext: Locator) extends Injector {
  override def plan(context: ModuleDef): FinalPlan = {
    parentContext.get[Planner].plan(context)
  }

  override def produce(diPlan: FinalPlan): Locator = {
    parentContext.get[TheFactoryOfAllTheFactories].produce(diPlan, parentContext)
  }
}
