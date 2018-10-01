package com.github.pshirshov.izumi.distage.provisioning.strategies

import com.github.pshirshov.izumi.distage.model.exceptions.{InvalidPlanException, MissingRefException, NoopProvisionerImplCalled, ProvisioningException}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.ClassStrategy
import com.github.pshirshov.izumi.distage.model.provisioning.{OpResult, ProvisioningKeyProvider}
import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.u._
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.fundamentals.reflection.{ReflectionUtil, TypeUtil}

class ClassStrategyDefaultImpl
(
  symbolIntrospector: SymbolIntrospector.Runtime
) extends ClassStrategy {
  def instantiateClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass): Seq[OpResult.NewInstance] = {

    import op._

    val targetType = wiring.instanceType

    val args = wiring.associations.map {
      key =>
        context.fetchKey(key.wireWith, key.isByName) match {
          case Some(dep) =>
            dep
          case _ =>
            throw new InvalidPlanException("The impossible happened! Tried to instantiate class," +
              s" but the dependency has not been initialized: dependency: ${key.wireWith} of class: $target")
        }
    }

    val instance = mkScala(context, targetType, args)
    Seq(OpResult.NewInstance(target, instance))
  }

  private def mkScala(context: ProvisioningKeyProvider, targetType: SafeType, args: Seq[Any]) = {
    val symbol = targetType.tpe.typeSymbol

    if (symbol.isModule) { // don't re-instantiate scala objects
      mirror.reflectModule(symbol.asModule).instance
    } else {
      val refClass = reflectClass(context, targetType, symbol)
      val ctorSymbol = symbolIntrospector.selectConstructorMethod(targetType)
      val refCtor = refClass.reflectConstructor(ctorSymbol)

      val hasByName = ctorSymbol.paramLists.exists(_.exists(v => v.isTerm && v.asTerm.isByNameParam))
      if (hasByName) { // this is a dirty workaround for crappy logic in JavaTransformingMethodMirror
        mkJava(targetType, args)
      } else {
        refCtor.apply(args: _*)
      }
    }
  }

  private def reflectClass(context: ProvisioningKeyProvider, targetType: SafeType, symbol: Symbol): ClassMirror = {
    if (!symbol.isStatic) {
      val typeRef = ReflectionUtil.toTypeRef(targetType.tpe)
        .getOrElse(throw new ProvisioningException(s"Expected TypeRefApi while processing $targetType, got ${targetType.tpe}", null))

      val prefix = typeRef.pre

      val module = if (prefix.termSymbol.isModule) {
        mirror.reflectModule(prefix.termSymbol.asModule).instance
      } else {
        val required = SafeType.apply(prefix)
        val key = DIKey.TypeKey(required)
        context.fetchUnsafe(key) match {
          case Some(value) =>
            value
          case None =>
            throw new MissingRefException(s"Cannot get instance of prefix type $key while processing $targetType", Set(key), None)
        }
      }

      mirror.reflect(module).reflectClass(symbol.asClass)
    } else {
      mirror.reflectClass(symbol.asClass)
    }
  }


  private def mkJava(targetType: SafeType, args: Seq[Any]): Any = {
    val refUniverse = RuntimeDIUniverse.mirror
    val clazz = refUniverse
      .runtimeClass(targetType.tpe)
    val argValues = args.map(_.asInstanceOf[AnyRef])

    clazz
      .getDeclaredConstructors
      .toList
      .filter(_.getParameterCount == args.size)
      .find {
        c =>
          c.getParameterTypes.zip(argValues).forall({ case (exp, impl) => TypeUtil.isAssignableFrom(exp, impl) })
      } match {
      case Some(constructor) =>
        constructor.setAccessible(true)
        constructor.newInstance(argValues: _*)

      case None =>
        throw new ProvisioningException(s"Can't find constructor for $targetType", null)
    }
  }


}

class ClassStrategyFailingImpl extends ClassStrategy {
  override def instantiateClass(context: ProvisioningKeyProvider, op: WiringOp.InstantiateClass): Seq[OpResult.NewInstance] = {
    Quirks.discard(context)
    throw new NoopProvisionerImplCalled(s"ClassStrategyFailingImpl does not support instantiation, failed op: $op", this)
  }
}
