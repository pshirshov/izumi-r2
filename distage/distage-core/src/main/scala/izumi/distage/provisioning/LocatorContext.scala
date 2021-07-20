package izumi.distage.provisioning

import izumi.distage.model.Locator
import izumi.distage.model.provisioning.Provision.ProvisionImmutable
import izumi.distage.model.provisioning.ProvisioningKeyProvider
import izumi.distage.model.provisioning.proxies.ProxyDispatcher.ByNameDispatcher
import izumi.distage.model.reflection.DIKey

final case class LocatorContext(
  provision: ProvisionImmutable[Any],
  locator: Locator,
) extends ProvisioningKeyProvider {

  override def fetchUnsafe(key: DIKey): Option[Any] = {
    provision
      .get(key).orElse(key match {
        case DIKey.ProxyInitKey(proxied) =>
          // see "keep proxies alive in case of intersecting loops" test
          // there may be a situation when we have intersecting loops resolved independently and
          // real implementation may be not available yet, while we process one of the loops
          // so in case we can't access "real" instance we may try to fallback to unitialized proxy instance
          provision.get(proxied)
        case _ =>
          None
      })
  }

  override def fetchKey(key: DIKey, byName: Boolean): Option[Any] = {
    fetchUnsafe(key).map {
      case dep: ByNameDispatcher =>
        if (byName) dep else dep.apply()
      case dep =>
        if (byName) () => dep else dep
    }
  }

  override def importKey(key: DIKey): Option[Any] = {
    locator.lookupInstance[Any](key)
  }

  override def instances: collection.Map[DIKey, Any] = {
    provision.instances
  }

  override def narrow(allRequiredKeys: Set[DIKey]): ProvisioningKeyProvider = {
    LocatorContext(provision.narrow(allRequiredKeys), locator)
  }
}
