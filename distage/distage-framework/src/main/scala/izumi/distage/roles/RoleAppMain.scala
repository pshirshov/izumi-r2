package izumi.distage.roles

import distage._
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain.{AdditionalRoles, ArgV}
import izumi.distage.roles.launcher.RoleAppLauncher
import izumi.distage.roles.launcher.services.AppFailureHandler
import izumi.distage.roles.launcher.services.StartupPlanExecutor.PreparedApp
import izumi.fundamentals.platform.cli.model.raw.RawRoleParams
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.language.unused

abstract class RoleAppMain[F[_]: TagK]() {
  def main(args: Array[String]): Unit = {
    try {
      val argv = ArgV(args)
      val appModule = makeAppModule(argv)
      val overrideModule = makeAppModuleOverride(argv)
      Injector.NoProxies().produceRun(appModule.overridenBy(overrideModule)) {
        appResource: DIResourceBase[Identity, PreparedApp[F]] =>
          appResource.use(_.run())
      }
    } catch {
      case t: Throwable =>
        createEarlyFailureHandler().onError(t)
    }
  }

  protected def requiredRoles(@unused args: ArgV): Vector[RawRoleParams] = {
    Vector.empty
  }

  protected def makeAppModuleOverride(@unused args: ArgV): Module = {
    Module.empty
  }

  protected def makeAppModule(args: ArgV): Module = {
    new MainAppModule[F](
      args,
      AdditionalRoles(requiredRoles(args)),
      ???,
      makePluginConfig(),
    )
  }

  protected def createEarlyFailureHandler(): AppFailureHandler = {
    AppFailureHandler.TerminatingHandler
  }

  protected def makeLauncher(): RoleAppLauncher[F]

  protected def makePluginConfig(): PluginConfig

}

object RoleAppMain {
  case class ArgV(args: Array[String])
  case class AdditionalRoles(knownRequiredRoles: Vector[RawRoleParams])
}
