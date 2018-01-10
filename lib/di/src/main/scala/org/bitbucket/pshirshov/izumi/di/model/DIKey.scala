package org.bitbucket.pshirshov.izumi.di.model

import org.bitbucket.pshirshov.izumi.di.{Symb, Tag}
import scala.reflect.runtime.universe._

sealed trait DIKey {
  def symbol: Symb
}

object DIKey {

  case class TypeKey(symbol: Symb) extends DIKey

  case class IdKey[InstanceId](symbol: Symb, id: InstanceId) extends DIKey

  def get[K: Tag]: TypeKey = TypeKey(typeTag[K].tpe.typeSymbol)
}



