package izumi.distage.model.plan.repr

import izumi.distage.constructors.DebugProperties
import izumi.fundamentals.platform.basics.IzBoolean.{all, any}
import izumi.fundamentals.platform.jvm.IzJvm

object DIRendering {
  val colorsEnabled: Boolean = any(
    all(
      DebugProperties.`izumi.distage.rendering.colored`.boolValue(true),
      IzJvm.terminalColorsEnabled,
    ),
    DebugProperties.`izumi.distage.rendering.colored.forced`.boolValue(false),
  )

}
