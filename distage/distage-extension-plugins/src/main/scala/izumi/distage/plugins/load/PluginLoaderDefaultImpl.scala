package izumi.distage.plugins.load

import io.github.classgraph.ClassGraph
import izumi.distage.plugins.{PluginBase, PluginConfig, PluginDef}
import izumi.fundamentals.platform.cache.SyncCache
import izumi.fundamentals.reflection.TypeUtil

import scala.jdk.CollectionConverters._
import scala.util.chaining.scalaUtilChainingOps

class PluginLoaderDefaultImpl extends PluginLoader {
  /** Will disable scanning if no packages are specified (add `"_root_"` package if you want to scan everything) */
  override def load(config: PluginConfig): LoadedPlugins = {
    val loadedPlugins = if (config.packagesEnabled.isEmpty && config.packagesDisabled.isEmpty) {
      Seq.empty
    } else {
      scanClasspath(config)
    }

    LoadedPlugins(loadedPlugins, config.merges, config.overrides)
  }

  protected[this] def scanClasspath(config: PluginConfig): Seq[PluginBase] = {
    val enabledPackages = config.packagesEnabled.filterNot(p => config.packagesDisabled.contains(p) || p == "_root_")
    val disabledPackages = config.packagesDisabled

    val pluginBase = classOf[PluginBase]
    val pluginDef = classOf[PluginDef[?]]
    val whitelistedClasses = Seq(pluginDef.getName)

    def loadPkgs(pkgs: Seq[String]): Seq[PluginBase] = {
      PluginLoaderDefaultImpl.doLoad[PluginBase](pluginBase.getName, whitelistedClasses, pkgs, disabledPackages, config.debug)
    }

    if (!config.cachePackages) {
      loadPkgs(enabledPackages)
    } else {
      val h1 = scala.util.hashing.MurmurHash3.seqHash(whitelistedClasses)
      val h2 = scala.util.hashing.MurmurHash3.seqHash(disabledPackages)
      enabledPackages.flatMap {
        pkg =>
          val key = s"$pkg;$h1;$h2"
          PluginLoaderDefaultImpl.cache.getOrCompute(key, loadPkgs(Seq(pkg)))
      }
    }
  }
}

object PluginLoaderDefaultImpl {
  def apply(): PluginLoaderDefaultImpl = new PluginLoaderDefaultImpl()

  private lazy val cache = new SyncCache[String, Seq[PluginBase]]()

  def doLoad[T](base: String, whitelistClasses: Seq[String], enabledPackages: Seq[String], disabledPackages: Seq[String], debug: Boolean): Seq[T] = {
    val scanResult = new ClassGraph()
      .acceptPackages(enabledPackages: _*)
      .acceptClasses(whitelistClasses :+ base: _*)
      .rejectPackages(disabledPackages: _*)
      .enableExternalClasses()
      .pipe(if (debug) _.verbose() else identity)
      .scan()

    try {
      val implementors = scanResult.getClassesImplementing(base)
      implementors.asScala
        .filterNot(c => c.isAbstract || c.isSynthetic || c.isAnonymousInnerClass)
        .flatMap {
          classInfo =>
            val clz = classInfo.loadClass()
            if (!TypeUtil.isAnonymous(clz)) {
              TypeUtil.isObject(clz) match {
                case Some(moduleField) =>
                  Seq(TypeUtil.instantiateObject[T](moduleField))
                case None =>
                  TypeUtil.instantiateZeroArgClass[T](clz).toSeq
              }
            } else {
              Nil
            }
        }
        .toSeq // 2.13 compat
    } finally {
      scanResult.close()
    }
  }
}
