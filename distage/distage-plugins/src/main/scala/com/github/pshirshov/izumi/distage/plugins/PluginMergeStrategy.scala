package com.github.pshirshov.izumi.distage.plugins

import com.github.pshirshov.izumi.distage.model.definition.{ModuleDef, PluginDef, TrivialModuleDef}

trait PluginMergeStrategy[T <: LoadedPlugins] {
  def merge[D <: PluginDef](defs: Seq[D]): T

  // TODO: configurable merge strategy allowing to enable/individual plugins
}

object SimplePluginMergeStrategy extends PluginMergeStrategy[LoadedPlugins] {
  override def merge[D <: PluginDef](defs: Seq[D]): LoadedPlugins = {
    val merged = defs.reduceLeftOption[ModuleDef](_ ++ _).getOrElse(TrivialModuleDef)

    JustLoadedPlugins(merged)
  }
}
