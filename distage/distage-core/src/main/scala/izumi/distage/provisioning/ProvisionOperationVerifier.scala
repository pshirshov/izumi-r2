package izumi.distage.provisioning

import izumi.distage.model.exceptions._
import izumi.distage.model.provisioning.proxies.ProxyDispatcher
import izumi.distage.model.provisioning.proxies.ProxyDispatcher.ByNameDispatcher
import izumi.distage.model.reflection.MirrorProvider
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.language.Quirks

trait ProvisionOperationVerifier {
  def verify(target: DIKey, prohibited: collection.Set[DIKey], value: Any, clue: String): Unit
}

object ProvisionOperationVerifier {

  class Default(
    mirror: MirrorProvider
  ) extends ProvisionOperationVerifier {

    def verify(target: DIKey, keys: scala.collection.Set[DIKey], value: Any, clue: String): Unit = {
      if (keys.contains(target)) throw DuplicateInstancesException(target)

      target match {
        case _: DIKey.ProxyControllerKey => // each proxy operation returns two assignments
          throwIfIncompatible(DIKey.get[ProxyDispatcher], clue, value)

        case _ =>
          value match {
            case d: ByNameDispatcher =>
              val dispatcherTypeCompatible = d.key.tpe == target.tpe || (d.key.tpe <:< target.tpe)

              if (!dispatcherTypeCompatible) {
                throw new IncompatibleTypesException(s"Dispatcher contains incompatible key: ${target.tpe}, found: ${d.key.tpe}", target.tpe, d.key.tpe)
              }

            case u =>
              throwIfIncompatible(target, clue, u)
          }
      }

    }

    private def throwIfIncompatible(target: DIKey, clue: String, value: Any): Unit = {
      if (!mirror.runtimeClassCompatible(target.tpe, value)) {
        throw new IncompatibleRuntimeClassException(target, value.getClass, clue)
      }
    }

  }

  object Null extends ProvisionOperationVerifier {
    override def verify(target: DIKey, keys: collection.Set[DIKey], value: Any, clue: String): Unit = {
      Quirks.discard(target, keys, value, clue)
    }
  }

}
