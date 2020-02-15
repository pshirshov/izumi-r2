package distage

import izumi.distage.bootstrap.{BootstrapLocator, CglibBootstrap}
import izumi.distage.model.definition.BootstrapContextModule
import izumi.distage.model.recursive.Bootloader
import izumi.distage.{InjectorFactory, InjectorDefaultImpl}

object Injector extends InjectorFactory {

  /**
    * Create a new Injector
    *
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  override def apply(overrides: BootstrapModule*): Injector = {
    bootstrap(CglibBootstrap.cogenBootstrap, overrides.merge)
  }

  /**
    * Create a new Injector with chosen [[izumi.distage.model.definition.Activation]] axes
    *
    * @param overrides Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                  They can be used to extend the Injector, e.g. add ability to inject config values
    */
  override def apply(activation: Activation, overrides: BootstrapModule*): Injector = {
    bootstrap(CglibBootstrap.cogenBootstrap, (overrides :+ activationModule(activation)).merge)
  }

  /**
    * Create a new Injector from a custom [[BootstrapContextModule]]
    *
    * @param bootstrapBase See [[BootstrapLocator]] and [[CglibBootstrap]] for a list available bootstrap modules
    * @param overrides     Optional: Overrides of Injector's own bootstrap environment - injector itself is constructed with DI.
    *                      They can be used to extend the Injector, e.g. add ability to inject config values
    */
  override def apply(bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector = {
    bootstrap(bootstrapBase, overrides.merge)
  }

  /**
    * Create a new injector inheriting configuration, hooks and the object graph from results of a previous Injector's run
    *
    * @param parent Instances from parent [[Locator]] will be available as imports in new Injector's [[izumi.distage.model.Producer#produce produce]]
    */
  override def inherit(parent: Locator): Injector = {
    new InjectorDefaultImpl(parent, this)
  }

  override def bootloader(input: PlannerInput, bootstrapModule: BootstrapModule = BootstrapModule.empty): Bootloader = {
    new Bootloader(bootstrapModule, input, this)
  }

  private[this] def bootstrap(bootstrapBase: BootstrapContextModule, overrides: BootstrapModule): Injector = {
    val bootstrapDefinition = bootstrapBase.overridenBy(overrides)
    val bootstrapLocator = new BootstrapLocator(bootstrapDefinition overridenBy new BootstrapModuleDef {
      make[BootstrapModule].fromValue(bootstrapDefinition)
    })
    inherit(bootstrapLocator)
  }

  object Standard extends InjectorBootstrap(CglibBootstrap.cogenBootstrap)

  /** Disable cglib proxies, but allow by-name parameters to resolve cycles */
  object NoProxies extends InjectorBootstrap(BootstrapLocator.noProxiesBootstrap)

  /** Disable all cycle resolution, immediately throw when circular dependencies are found, whether by-name or not */
  object NoCycles extends InjectorBootstrap(BootstrapLocator.noCyclesBootstrap)

  private[Injector] sealed abstract class InjectorBootstrap(bootstrapContextModule: BootstrapContextModule) extends InjectorFactory {
    override final def apply(overrides: BootstrapModule*): Injector = {
      bootstrap(bootstrapContextModule, overrides.merge)
    }

    override final def apply(activation: Activation, overrides: BootstrapModule*): Injector = {
      bootstrap(bootstrapContextModule, (overrides :+ activationModule(activation)).merge)
    }

    override final def apply(bootstrapBase: BootstrapContextModule, overrides: BootstrapModule*): Injector = {
      bootstrap(bootstrapBase, overrides.merge)
    }

    override final def inherit(parent: Locator): Injector = {
      new InjectorDefaultImpl(parent, this)
    }

    override final def bootloader(input: PlannerInput, bootstrapModule: BootstrapModule = BootstrapModule.empty): Bootloader = {
      new Bootloader(bootstrapModule, input, this)
    }
  }

  private[this] def activationModule(activation: Activation): BootstrapModuleDef = new BootstrapModuleDef {
    make[Activation].fromValue(activation)
  }
}




