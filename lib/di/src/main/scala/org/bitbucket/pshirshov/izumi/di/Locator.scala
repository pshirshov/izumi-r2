package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.exceptions.MissingInstanceException

import scala.reflect.runtime.universe._

case class TypedRef[+T:Tag](value: T) {
  def symbol: Symb = typeTag[T].tpe.typeSymbol
}

trait Locator {
  final def find[T: Tag]: Option[T] = lookupInstance(DIKey.get[T])

  final def find[T: Tag, Id](id: Id): Option[T] = lookupInstance(DIKey.get[T].narrow(id))

  final def get[T: Tag]: T = lookupInstanceOrThrow(DIKey.get[T])

  final def get[T: Tag, Id](id: Id): T = lookupInstanceOrThrow(DIKey.get[T].narrow(id))

  def parent: Option[Locator]

  protected def unsafeLookup(key:DIKey): Option[AnyRef]

  protected def lookup[T: Tag](key: DIKey): Option[TypedRef[T]] = {
    unsafeLookup(key)
      .filter(_ => key.symbol.info.baseClasses.contains(typeTag[T].tpe.typeSymbol))
      .map {
        value =>
          TypedRef[T](value.asInstanceOf[T])
      }
  }

  protected final def lookupInstanceOrThrow[T: Tag](key: DIKey): T = {
    lookupInstance(key) match {
      case Some(v) =>
        v

      case None =>
        throw new MissingInstanceException(s"Instance is not available in the context: $key", key)
    }
  }

  protected final def lookupInstance[T: Tag](key: DIKey): Option[T] = {
    recursiveLookup(key)
      .map(_.value)
    //.filter(t => isInstanceOf(key, t))
  }

  protected final def recursiveLookup[T:Tag](key: DIKey): Option[TypedRef[T]] = {
    interceptor.interceptLookup[T](key, this).orElse(
      lookup(key)
        .orElse(parent.flatMap(_.lookup(key)))
    )
  }

  protected final def interceptor: LookupInterceptor = lookup[LookupInterceptor](DIKey.get[LookupInterceptor])
    .map(_.value)
    .getOrElse(NullLookupInterceptor.instance)
}
