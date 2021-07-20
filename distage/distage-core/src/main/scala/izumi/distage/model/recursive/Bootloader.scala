package izumi.distage.model.recursive

import izumi.distage.InjectorFactory
import izumi.distage.model.definition.{Activation, BootstrapModule, Id, Module, ModuleBase}
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.plan.{DIPlan, Roots}
import izumi.distage.model.{Injector, PlannerInput}
import izumi.distage.modules.DefaultModule
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.TagK

final case class BootstrappedApp(
  injector: Injector[Identity],
  module: ModuleBase,
  plan: DIPlan,
)

final case class BootConfig(
  bootstrap: BootstrapModule => BootstrapModule = identity,
  appModule: ModuleBase => ModuleBase = identity,
  activation: Activation => Activation = identity,
  bootstrapActivation: Activation => Activation = identity,
  roots: Roots => Roots = identity,
)

class Bootloader(
  val injectorFactory: InjectorFactory,
  val bootstrapModule: BootstrapModule,
  val bootstrapActivation: Activation @Id("bootstrapActivation"),
  val defaultModule: Module @Id("defaultModule"),
  val input: PlannerInput,
) {
  def boot(config: BootConfig): BootstrappedApp = {
    val activation = config.activation(input.activation)
    val bootstrap = config.bootstrap(bootstrapModule)
    val injector = injectorFactory[Identity](
      bootstrapActivation = config.bootstrapActivation(bootstrapActivation),
      overrides = Seq(bootstrap),
    )(
      QuasiIO[Identity],
      TagK[Identity],
      DefaultModule[Identity](defaultModule),
    )
    val module = config.appModule(input.bindings)
    val roots = config.roots(input.roots)
    val plan = injector.plan(PlannerInput(module, activation, roots))
    BootstrappedApp(injector, module, plan)
  }
}
