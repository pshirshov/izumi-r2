package izumi.distage.model.reflection

import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.macrortti.LightTypeTag
import izumi.reflect.{Tag, TagK, WeakTag}

final case class SafeType private[izumi] (
  tag: LightTypeTag,
  /*private[distage] val */ cls: Class[?],
) {
  override def toString: String = tag.repr

  override def hashCode: Int = tag.hashCode()
  override def equals(obj: Any): Boolean = {
    obj match {
      case that: SafeType =>
        tag =:= that.tag
      case _ =>
        false
    }
  }
  def =:=(that: SafeType): Boolean = tag =:= that.tag
  def <:<(that: SafeType): Boolean = tag <:< that.tag

  final def hasPreciseClass: Boolean = {
    try tag.shortName == cls.getSimpleName
    catch {
      case i: InternalError if i.getMessage == "Malformed class name" =>
        tag.shortName == cls.getName.reverse.takeWhile(c => c != '$' && c != '.').reverse
    }
  }
}

object SafeType {
  def get[T: Tag]: SafeType = SafeType(Tag[T].tag, Tag[T].closestClass)
  def getK[K[_]: TagK]: SafeType = SafeType(TagK[K].tag, TagK[K].closestClass)
  def unsafeGetWeak[T](implicit weakTag: WeakTag[T]): SafeType = SafeType(weakTag.tag, weakTag.closestClass)

  lazy val identityEffectType: SafeType = SafeType.getK[Identity]
}
