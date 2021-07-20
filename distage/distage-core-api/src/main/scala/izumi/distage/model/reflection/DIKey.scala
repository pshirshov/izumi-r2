package izumi.distage.model.reflection

import izumi.distage.model.definition.{Identifier, ImplDef}
import izumi.reflect.Tag

sealed trait DIKey {
  def tpe: SafeType
  override final lazy val hashCode: Int = scala.util.hashing.MurmurHash3.productHash(this.asInstanceOf[Product])
  protected def formatWithIndex(base: String, index: Option[Int]): String = {
    index match {
      case Some(value) =>
        s"$base::$value"
      case None =>
        base
    }
  }
}

object DIKey {
  def apply[T: Tag]: DIKey = DIKey.TypeKey(SafeType.get[T])
  def apply[T: Tag](id: Identifier): DIKey = DIKey.IdKey(SafeType.get[T], id.id)(id.idContract)

  def get[T: Tag]: DIKey.TypeKey = DIKey.TypeKey(SafeType.get[T])

  sealed trait BasicKey extends DIKey {
    def withTpe(tpe: SafeType): DIKey.BasicKey
  }

  final case class TypeKey(tpe: SafeType, mutatorIndex: Option[Int] = None) extends BasicKey {
    def named[I: IdContract](id: I): IdKey[I] = IdKey(tpe, id, mutatorIndex)
    def named(id: Identifier): IdKey[id.Id] = IdKey(tpe, id.id, mutatorIndex)(id.idContract)

    override def withTpe(tpe: SafeType): DIKey.TypeKey = copy(tpe = tpe)
    override def toString: String = formatWithIndex(s"{type.${tpe.toString}}", mutatorIndex)
  }

  final case class IdKey[I: IdContract](tpe: SafeType, id: I, mutatorIndex: Option[Int] = None) extends BasicKey {
    val idContract: IdContract[I] = implicitly
    def withMutatorIndex(index: Option[Int]): IdKey[I] = copy(mutatorIndex = index)
    override def withTpe(tpe: SafeType): DIKey.IdKey[I] = copy(tpe = tpe)
    override def toString: String = formatWithIndex(s"{type.${tpe.toString}@${idContract.repr(id)}}", mutatorIndex)
  }

  sealed trait SetKeyMeta
  object SetKeyMeta {
    case object NoMeta extends SetKeyMeta
    final case class WithImpl(disambiguator: ImplDef) extends SetKeyMeta
    final case  class WithAutoset(base: DIKey) extends SetKeyMeta
  }
  /**
    * @param set       Key of the parent Set. `set.tpe` must be of type `Set[T]`
    * @param reference Key of `this` individual element. `reference.tpe` must be a subtype of `T`
    */
  final case class SetElementKey(set: DIKey, reference: DIKey, disambiguator: SetKeyMeta) extends DIKey {
    override def tpe: SafeType = reference.tpe

    override def toString: String = {
      val drepr = (disambiguator match {
        case SetKeyMeta.NoMeta =>
          None
        case SetKeyMeta.WithImpl(disambiguator) =>
          Some(s"impl:${disambiguator.hashCode}")
        case SetKeyMeta.WithAutoset(base) =>
          Some(s"autoset:${base.toString}")
      }).map(v => "#" + v).getOrElse("")
      s"{set.$set/${reference.toString}$drepr"
    }
  }

  final case class ProxyInitKey(proxied: DIKey) extends DIKey {

    override def tpe: SafeType = proxied.tpe

    override def toString: String = s"{proxyinit.${proxied.toString}}"
  }

  final case class ProxyControllerKey(proxied: DIKey, tpe: SafeType) extends DIKey {
    override def toString: String = s"{proxyref.${proxied.toString}}"
  }

  final case class ResourceKey(key: DIKey, tpe: SafeType) extends DIKey {
    override def toString: String = s"{resource.${key.toString}/$tpe}"
  }

  final case class EffectKey(key: DIKey, tpe: SafeType) extends DIKey {
    override def toString: String = s"{effect.${key.toString}/$tpe}"
  }

  final case class MultiSetImplId(set: DIKey, impl: ImplDef)
  object MultiSetImplId {
    implicit object SetImplIdContract extends IdContract[MultiSetImplId] {
      override def repr(v: MultiSetImplId): String = s"set/${v.set}#${v.impl.hashCode}"
    }
  }
}
