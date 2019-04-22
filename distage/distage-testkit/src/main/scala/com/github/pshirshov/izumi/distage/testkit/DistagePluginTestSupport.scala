package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.app.BootstrapConfig
import com.github.pshirshov.izumi.distage.model.definition.BindingTag
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.plugins.MergedPlugins
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.distage.plugins.merge.ConfigurablePluginMergeStrategy.PluginMergeConfig
import com.github.pshirshov.izumi.distage.plugins.merge.{ConfigurablePluginMergeStrategy, PluginMergeStrategy}
import com.github.pshirshov.izumi.distage.roles.RolesInfo
import com.github.pshirshov.izumi.distage.roles.services.{PluginSource, PluginSourceImpl}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.config.AppConfig

import scala.collection.mutable

abstract class DistagePluginTestSupport[F[_] : TagK] extends DistageTestSupport[F] {

  /**
    * This may be used as an implementation of [[pluginPackages]] in simple cases.
    *
    * Though it has to be always explicitly specified because this behaviour applied by default
    * would be very obscure.
    */
  protected final def thisPackage: Seq[String] = Seq(this.getClass.getPackage.getName)

  protected def pluginPackages: Seq[String]

  protected def pluginBootstrapPackages: Option[Seq[String]] = None

  final protected def loadEnvironment(config: AppConfig, logger: IzLogger): TestEnvironment = {
    if (memoizePlugins) {
      DistagePluginTestSupport.getMemoizedEnv(getClass.getClassLoader, doLoad(logger))
    } else {
      doLoad(logger)
    }
  }

  private def doLoad(logger: IzLogger): TestEnvironment = {
    val roles = loadRoles(logger)
    val plugins = makePluginLoader(bootstrapConfig).load()
    val mergeStrategy = makeMergeStrategy(logger)
    val defBs: MergedPlugins = mergeStrategy.merge(plugins.bootstrap)
    val defApp: MergedPlugins = mergeStrategy.merge(plugins.app)
    val loadedBsModule = defBs.definition
    val loadedAppModule = defApp.definition
    TestEnvironment(
      loadedBsModule,
      loadedAppModule,
      roles,
    )
  }

  protected def memoizePlugins: Boolean = {
    Option(System.getProperty("izumi.distage.testkit.plugins.memoize", "true")).contains("true")
  }

  protected def loadRoles(logger: IzLogger): RolesInfo = {
    Quirks.discard(logger)
    // For all normal scenarios we don't need roles to setup a test
    RolesInfo(Set.empty, Seq.empty, Seq.empty, Seq.empty, Set.empty)
  }
  protected def disabledTags: BindingTag.Expressions.Expr = BindingTag.Expressions.False

  protected def makeMergeStrategy(lateLogger: IzLogger): PluginMergeStrategy = {
    Quirks.discard(lateLogger)
    new ConfigurablePluginMergeStrategy(PluginMergeConfig(
      disabledTags
      , Set.empty
      , Set.empty
      , Map.empty
    ))
  }

  protected def bootstrapConfig: BootstrapConfig = {
    BootstrapConfig(
      PluginConfig(debug = false, pluginPackages, Seq.empty),
      pluginBootstrapPackages.map(p => PluginConfig(debug = false, p, Seq.empty)),
    )
  }

  protected def makePluginLoader(bootstrapConfig: BootstrapConfig): PluginSource = {
    new PluginSourceImpl(bootstrapConfig)
  }

}

object DistagePluginTestSupport {
  private val memoizedPlugins = mutable.HashMap[AnyRef, TestEnvironment]()

  def getMemoizedEnv(classloader: ClassLoader, default: => TestEnvironment): TestEnvironment = memoizedPlugins.synchronized {
    memoizedPlugins.get(classloader) match {
      case Some(value) =>
        value
      case None =>
        memoizedPlugins.put(classloader, default)
        default
    }
  }
}
