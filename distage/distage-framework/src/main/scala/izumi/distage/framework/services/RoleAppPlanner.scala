package izumi.distage.framework.services

import distage.Injector
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.framework.services.RoleAppPlanner.AppStartupPlans
import izumi.distage.model.definition.{Activation, BootstrapModule, Id}
import izumi.distage.model.effect.{QuasiAsync, QuasiIO, QuasiIORunner}
import izumi.distage.model.plan.{DIPlan, Roots, TriSplittedPlan}
import izumi.distage.model.recursive.{BootConfig, Bootloader}
import izumi.distage.model.reflection.DIKey
import izumi.distage.modules.DefaultModule
import izumi.fundamentals.platform.functional.Identity
import izumi.logstage.api.IzLogger
import izumi.reflect.TagK

trait RoleAppPlanner {
  def reboot(bsModule: BootstrapModule): RoleAppPlanner
  def makePlan(appMainRoots: Set[DIKey] /*, appModule: ModuleBase*/ ): AppStartupPlans
}

object RoleAppPlanner {

  final case class AppStartupPlans(
    runtime: DIPlan,
    app: TriSplittedPlan,
    injector: Injector[Identity],
  )

  class Impl[F[_]: TagK](
    options: PlanningOptions,
    activation: Activation @Id("roleapp"),
    bsModule: BootstrapModule @Id("roleapp"),
    bootloader: Bootloader @Id("roleapp"),
    logger: IzLogger,
  )(implicit
    defaultModule: DefaultModule[F]
  ) extends RoleAppPlanner { self =>

    private[this] val runtimeGcRoots: Set[DIKey] = Set(
      DIKey.get[QuasiIORunner[F]],
      DIKey.get[QuasiIO[F]],
      DIKey.get[QuasiAsync[F]],
    )

    override def reboot(bsOverride: BootstrapModule): RoleAppPlanner = {
      new RoleAppPlanner.Impl[F](options, activation, bsModule overriddenBy bsOverride, bootloader, logger)
    }

    override def makePlan(appMainRoots: Set[DIKey]): AppStartupPlans = {
      val runtimeBsApp = bootloader.boot(
        BootConfig(
          bootstrap = _ => bsModule,
          activation = _ => activation,
          roots = _ => Roots(runtimeGcRoots),
        )
      )
      val runtimeKeys = runtimeBsApp.plan.keys

      val appPlan = runtimeBsApp.injector.ops.trisectByKeys(activation, runtimeBsApp.module.drop(runtimeKeys), appMainRoots) {
        _.collectChildrenKeysSplit[IntegrationCheck[Identity], IntegrationCheck[F]]
      }

      val check = new PlanCircularDependencyCheck(options, logger)
      check.verify(runtimeBsApp.plan)
      check.verify(appPlan.shared)
      check.verify(appPlan.side)
      check.verify(appPlan.primary)

      logger.info(
        s"Planning finished. ${appPlan.primary.keys.size -> "main ops"}, ${appPlan.side.keys.size -> "integration ops"}, ${appPlan.shared.keys.size -> "shared ops"}, ${runtimeBsApp.plan.keys.size -> "runtime ops"}"
      )
      AppStartupPlans(runtimeBsApp.plan, appPlan, runtimeBsApp.injector)
    }

  }

}
