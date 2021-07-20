package izumi.distage.model.definition

import izumi.distage.constructors.AnyConstructor
import izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.DIKey.SetKeyMeta
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag

object Bindings {
  def binding[T: Tag: AnyConstructor](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    provider[T](AnyConstructor[T])

  def binding[T: Tag, I <: T: Tag: AnyConstructor](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    provider[T](AnyConstructor[I])

  def binding[T: Tag, I <: T: Tag](instance: I)(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(SafeType.get[I], instance), Set.empty, pos.get.position)

  def instance[T: Tag](instance: T)(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.InstanceImpl(SafeType.get[T], instance), Set.empty, pos.get.position)

  def reference[T: Tag, I <: T: Tag](implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.ReferenceImpl(SafeType.get[I], DIKey.get[I], weak = false), Set.empty, pos.get.position)

  def provider[T: Tag](function: Functoid[T])(implicit pos: CodePositionMaterializer): SingletonBinding[DIKey.TypeKey] =
    SingletonBinding(DIKey.get[T], ImplDef.ProviderImpl(function.get.ret, function.get), Set.empty, pos.get.position)

  def emptySet[T](implicit tag: Tag[Set[T]], pos: CodePositionMaterializer): EmptySetBinding[DIKey.TypeKey] =
    EmptySetBinding(DIKey.get[Set[T]], Set.empty, pos.get.position)

  def setElement[T: Tag, I <: T: Tag: AnyConstructor](implicit pos: CodePositionMaterializer): SetElementBinding = {
    setElementProvider[T](AnyConstructor[I])
  }

  def setElement[T: Tag, I <: T: Tag](instance: I)(implicit pos: CodePositionMaterializer): SetElementBinding = {
    val setkey = DIKey.get[Set[T]]
    val implKey = DIKey.get[I]
    val implDef = ImplDef.InstanceImpl(SafeType.get[I], instance)
    SetElementBinding(DIKey.SetElementKey(setkey, implKey, SetKeyMeta.WithImpl(implDef)), implDef, Set.empty, pos.get.position)
  }

  def setElementProvider[T: Tag](function: Functoid[T])(implicit pos: CodePositionMaterializer): SetElementBinding = {
    val setkey = DIKey.get[Set[T]]
    val implKey = DIKey.TypeKey(function.get.ret)
    val implDef = ImplDef.ProviderImpl(function.get.ret, function.get)
    SetElementBinding(DIKey.SetElementKey(setkey, implKey, SetKeyMeta.WithImpl(implDef)), implDef, Set.empty, pos.get.position)
  }

  def todo[K <: DIKey](key: K)(implicit pos: CodePositionMaterializer): SingletonBinding[K] = {
    val provider = Functoid.todoProvider(key)(pos).get
    SingletonBinding(key, ImplDef.ProviderImpl(provider.ret, provider), Set.empty, pos.get.position)
  }
}
