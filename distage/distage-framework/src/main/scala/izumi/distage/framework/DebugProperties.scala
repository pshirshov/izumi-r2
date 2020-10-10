package izumi.distage.framework

import izumi.fundamentals.platform.properties.DebugProperties

object DebugProperties extends DebugProperties {
  final val `distage.plancheck.check-config` = BoolProperty("distage.plancheck.check-config")
  final val `distage.plancheck.max-activations` = StrProperty("distage.plancheck.max-activations")
}
