package izumi.distage

import izumi.distage.model._
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.definition.{Activation, BootstrapModule, Lifecycle, Module, ModuleBase, ModuleDef}
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.plan.DIPlan
import izumi.distage.model.planning.PlanSplittingOps
import izumi.distage.model.provisioning.PlanInterpreter
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.recursive.{Bootloader, LocatorRef}
import izumi.distage.model.reflection.DIKey
import izumi.reflect.TagK

/**
  * @param bootstrapLocator contains Planner & PlanInterpeter built using a `BootstrapModule`,
  *                         most often created using [[izumi.distage.bootstrap.BootstrapLocator.bootstrap]]
  *
  * @param parentFactory passed-through for summoning in DI as `InjectorFactory` / field in `Bootloader`
  *
  * @param defaultModule is added to (but overridden by) user's [[izumi.distage.model.PlannerInput PlannerInput]]
  */
final class InjectorDefaultImpl[F[_]](
  val parentFactory: InjectorFactory,
  val bootstrapLocator: Locator,
  val defaultModule: Module,
)(implicit
  override val F: QuasiIO[F],
  override val tagK: TagK[F],
) extends Injector[F] {

  private[this] val planner: Planner = bootstrapLocator.get[Planner]
  private[this] val interpreter: PlanInterpreter = bootstrapLocator.get[PlanInterpreter]
  // passed-through into `Bootloader`
  private[this] val bsModule: BootstrapModule = bootstrapLocator.get[BootstrapModule]
  def ops: PlanSplittingOps = new PlanSplittingOps(this)

  override def plan(input: PlannerInput): DIPlan = {
    planner.plan(addSelfInfo(input))
  }

  override def planNoRewrite(input: PlannerInput): DIPlan = {
    planner.planNoRewrite(addSelfInfo(input))
  }

  override def planSafe(input: PlannerInput): Either[List[DIError], DIPlan] = {
    planner.planSafe(addSelfInfo(input))
  }

  override def planNoRewriteSafe(input: PlannerInput): Either[List[DIError], DIPlan] = {
    planner.planNoRewriteSafe(addSelfInfo(input))
  }

  override def rewrite(module: ModuleBase): ModuleBase = {
    planner.rewrite(module)
  }

  override private[distage] def produceDetailedFX[G[_]: TagK: QuasiIO](
    plan: DIPlan,
    filter: FinalizerFilter[G],
  ): Lifecycle[G, Either[FailedProvision[G], Locator]] = {
    interpreter.run[G](plan, bootstrapLocator, filter)
  }

  // TODO: probably this should be a part of the Planner itself
  private[this] def addSelfInfo(input: PlannerInput): PlannerInput = {
    val selfReflectionModule = InjectorDefaultImpl.selfReflectionModule(parentFactory, bsModule, defaultModule, input.activation, input)

    input.copy(
      bindings = ModuleBase.make(
        ModuleBase
          .overrideImpl(
            ModuleBase.overrideImpl(
              defaultModule.iterator,
              input.bindings.iterator,
            ),
            selfReflectionModule.iterator,
          ).toSet
      )
    )
  }

  override def providedEnvironment: InjectorProvidedEnv = {
    InjectorProvidedEnv(
      injectorFactory = parentFactory,
      bootstrapModule = bsModule,
      bootstrapLocator = bootstrapLocator,
      defaultModule = defaultModule,
      planner = planner,
      interpreter = interpreter,
    )
  }

  override def providedKeys: Set[DIKey] = {
    val parentLocatorKeys = bootstrapLocator.allInstances.iterator.map(_.key)
    val selfReflectionKeys = InjectorDefaultImpl.providedKeys
    val defaultModuleKeys = defaultModule.keys

    (parentLocatorKeys ++
    selfReflectionKeys.iterator ++
    defaultModuleKeys.iterator).toSet
  }

}

object InjectorDefaultImpl {
  private def selfReflectionModule(
    parentFactory: InjectorFactory,
    bsModule: BootstrapModule,
    defaultModule: Module,
    activation: Activation,
    input: PlannerInput,
  ): ModuleDef = {
    new ModuleDef {
      make[Bootloader]
      // Bootloader dependencies
      make[InjectorFactory].fromValue(parentFactory)
      make[BootstrapModule].fromValue(bsModule)
      make[Module].named("defaultModule").fromValue(defaultModule)
      make[PlannerInput].fromValue(input)
      // not required by Bootloader
      make[Activation].fromValue(activation)
    }
  }

  private[this] lazy val selfReflectionKeys: Set[DIKey] = {
    // passing nulls to prevent key list getting out of sync
    selfReflectionModule(null, null, null, null.asInstanceOf[Activation], null).keys
  }

  lazy val providedKeys: Set[DIKey] = {
    selfReflectionKeys +
    DIKey[LocatorRef] // magic import, always available
  }
}
