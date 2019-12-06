package izumi.distage.testkit.services.dstest

import distage._
import izumi.distage.framework.activation.PruningPlanMergingPolicy
import izumi.distage.model.definition.Activation
import izumi.distage.model.planning.PlanMergingPolicy
import izumi.distage.roles.meta.RolesInfo
import izumi.distage.roles.model.ActivationInfo

final case class TestEnvironment(
                                  private val baseBsModule: ModuleBase,
                                  appModule: ModuleBase,
                                  roles: RolesInfo,
                                  activationInfo: ActivationInfo,
                                  activation: Activation,
                                  memoizedKeys: DIKey => Boolean,
                                ) {
  def bsModule: ModuleBase = baseBsModule overridenBy new BootstrapModuleDef {
    make[PlanMergingPolicy].from[PruningPlanMergingPolicy]
    make[Activation].from(activation)
  }
}
