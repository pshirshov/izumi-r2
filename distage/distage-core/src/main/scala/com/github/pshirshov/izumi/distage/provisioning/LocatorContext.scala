package com.github.pshirshov.izumi.distage.provisioning

import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.model.provisioning.{Provision, ProvisioningContext}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeUniverse

case class LocatorContext(provision: Provision, locator: Locator) extends ProvisioningContext {
  override def fetchKey(key: RuntimeUniverse.DIKey): Option[Any] = provision.get(key)

  override def importKey(key: RuntimeUniverse.DIKey): Option[Any] = locator.lookupInstance[Any](key)

  override def narrow(allRequiredKeys: Set[RuntimeUniverse.DIKey]): ProvisioningContext = {
    LocatorContext(provision.narrow(allRequiredKeys), locator)
  }

  override def extend(values: Map[RuntimeUniverse.DIKey, Any]): ProvisioningContext = {
    LocatorContext(provision.extend(values: Map[RuntimeUniverse.DIKey, Any]), locator)
  }
}
