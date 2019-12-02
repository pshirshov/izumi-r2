package izumi.distage.reflection

import izumi.distage.model.definition.{Id, With}
import izumi.distage.model.exceptions.{BadIdAnnotationException, UnsupportedDefinitionException, UnsupportedWiringException}
import izumi.distage.model.reflection.ReflectionProvider
import izumi.distage.model.reflection.universe.DIUniverse
import izumi.fundamentals.reflection.ReflectionUtil
import izumi.fundamentals.reflection.macrortti.LightTypeTag.ReflectionLock

trait ReflectionProviderDefaultImpl extends ReflectionProvider {
  self =>

  import u._
  import Wiring._

  // dependencykeyprovider

  private[this] def keyFromParameter(parameterSymbol: SymbolInfo): DIKey.BasicKey = {
    val typeKey = if (parameterSymbol.isByName) {
      DIKey.TypeKey(SafeType(parameterSymbol.finalResultType.use(_.typeArgs.head.finalResultType)))
    } else {
      DIKey.TypeKey(parameterSymbol.finalResultType)
    }

    withIdKeyFromAnnotation(parameterSymbol, typeKey)
  }

  override def associationFromParameter(parameterSymbol: SymbolInfo): Association.Parameter = {
    val key = keyFromParameter(parameterSymbol)
    Association.Parameter(parameterSymbol, key)
  }

  private[this] def keyFromMethod(methodSymbol: SymbolInfo): DIKey.BasicKey = {
    val typeKey = DIKey.TypeKey(methodSymbol.finalResultType)
    withIdKeyFromAnnotation(methodSymbol, typeKey)
  }

  private[this] def withIdKeyFromAnnotation(parameterSymbol: SymbolInfo, typeKey: DIKey.TypeKey): DIKey.BasicKey =
    parameterSymbol.findUniqueAnnotation(typeOfIdAnnotation) match {
      case Some(Id(name)) =>
        typeKey.named(name)
      case Some(v) =>
        throw new BadIdAnnotationException(typeOfIdAnnotation.toString, v)
      case None =>
        typeKey
    }

  // reflectionprovider

  override def symbolToWiring(tpe: TypeNative): Wiring.PureWiring = ReflectionLock.synchronized {
    tpe match {
      case FactorySymbol(factoryMethods, dependencyMethods) =>
        val unsafeSafeType = SafeType(tpe)

        val mw = factoryMethods.map(_.asMethod).map {
          factoryMethod =>
            val factoryMethodSymb = SymbolInfo.Runtime(factoryMethod, unsafeSafeType, wasGeneric = false)

            val resultType = resultOfFactoryMethod(factoryMethodSymb)

            val alreadyInSignature = {
              selectNonImplicitParameters(factoryMethod)
                .flatten
                .map(p => keyFromParameter(SymbolInfo.Runtime(p, unsafeSafeType, wasGeneric = false)))
            }

            val methodTypeWireable = mkConstructorWiring(resultType)

            val excessiveSymbols = alreadyInSignature.toSet -- methodTypeWireable.requiredKeys

            if (excessiveSymbols.nonEmpty) {
              throw new UnsupportedDefinitionException(
                s"""Augmentation failure.
                   |  * Type $tpe has been considered a factory because of abstract method `$factoryMethodSymb` with result type `$resultType`
                   |  * But method signature contains unrequired symbols: $excessiveSymbols
                   |  * Only the following symbols are requird: ${methodTypeWireable.requiredKeys}
                   |  * This may happen in case you unintentionally bind an abstract type (trait, etc) as implementation type.""".stripMargin)
            }

            Wiring.Factory.FactoryMethod(factoryMethodSymb, methodTypeWireable, alreadyInSignature)
        }

        val materials = dependencyMethods.map(methodToAssociation(tpe, _))

        Wiring.Factory(unsafeSafeType, mw, materials)

      case _ =>
        mkConstructorWiring(tpe)
    }
  }

  override def constructorParameterLists(symbl: TypeNative): List[List[Association.Parameter]] = ReflectionLock.synchronized {
    selectConstructorArguments(symbl).toList.flatten.map(_.map(associationFromParameter))
  }

  private[this] def mkConstructorWiring(symbl: TypeNative): SingletonWiring.ReflectiveInstantiationWiring = symbl match {
    case ConcreteSymbol(symb) =>
      SingletonWiring.Constructor(SafeType(symb), constructorParameters(symb), getPrefix(symb))

    case AbstractSymbol(symb) =>
      SingletonWiring.AbstractSymbol(SafeType(symb), traitMethods(symb), getPrefix(symb))

    case FactorySymbol(_, _) =>
      throw new UnsupportedWiringException(s"Factory cannot produce factories, it's pointless: $symbl", SafeType(symbl))

    case _ =>
      throw new UnsupportedWiringException(s"Wiring unsupported: $symbl", SafeType(symbl))
  }

  private[this] def constructorParameters(symbl: TypeNative): List[Association.Parameter] = {
    ReflectionLock.synchronized {
      constructorParameterLists(symbl).flatten
    }
  }

  private[this] def getPrefix(symb: TypeNative): Option[DIKey] = {
    if (symb.typeSymbol.isStatic) {
      None
    } else {
      val typeRef = ReflectionUtil.toTypeRef[u.u.type](symb)
      typeRef
        .map(_.pre)
        .filterNot(m => m.termSymbol.isModule && m.termSymbol.isStatic)
        .map(v => DIKey.TypeKey(SafeType(v)))
    }
  }

  private[this] def resultOfFactoryMethod(symbolInfo: SymbolInfo): TypeNative = {
    symbolInfo.findUniqueAnnotation(typeOfWithAnnotation) match {
      case Some(With(tpe)) =>
        tpe
      case _ =>
        symbolInfo.finalResultType.use(identity)
    }
  }

  private[this] def traitMethods(tpe: TypeNative): List[Association.AbstractMethod] = {
    // empty paramLists means parameterless method, List(List()) means nullarg unit method()
    val declaredAbstractMethods = tpe.members
      .sorted // preserve same order as definition ordering because we implicitly depend on it elsewhere
      .filter(isWireableMethod)
      .map(_.asMethod)
    declaredAbstractMethods.map(methodToAssociation(tpe, _))
  }

  private[this] def methodToAssociation(tpe: TypeNative, method: MethodSymbNative): Association.AbstractMethod = {
    val methodSymb = SymbolInfo.Runtime(method, SafeType(tpe), wasGeneric = false)
    Association.AbstractMethod(methodSymb, keyFromMethod(methodSymb))
  }

  private object ConcreteSymbol {
    def unapply(arg: TypeNative): Option[TypeNative] = Some(arg).filter(isConcrete)
  }

  private object AbstractSymbol {
    def unapply(arg: TypeNative): Option[TypeNative] = Some(arg).filter(isWireableAbstract)
  }

  private object FactorySymbol {
    def unapply(arg: TypeNative): Option[(List[SymbNative], List[MethodSymbNative])] =
      Some(arg)
        .filter(isFactory)
        .map(f =>
          (f.members.filter(isFactoryMethod).toList,
           f.members.filter(isWireableMethod).map(_.asMethod).toList,
          ))
  }

  // symbolintrospector
  private[this] def selectConstructorArguments(tpe: TypeNative): Option[List[List[SymbolInfo]]] = ReflectionLock.synchronized {
    selectConstructorMethod(tpe).map {
      selectedConstructor =>
        val originalParamListTypes = selectedConstructor.paramLists.map(_.map(_.typeSignature))
        val paramLists = selectedConstructor.typeSignatureIn(tpe).paramLists
        // Hack due to .typeSignatureIn throwing out type annotations...
        originalParamListTypes
          .zip(paramLists)
          .map {
            case (origTypes, params) =>
              origTypes.zip(params).map {
                case (o: u.u.AnnotatedTypeApi, p) =>
                  SymbolInfo.Runtime(p, SafeType(tpe), o.underlying.typeSymbol.isParameter, o.annotations)
                case (o, p) =>
                  SymbolInfo.Runtime(p, SafeType(tpe), o.typeSymbol.isParameter)
              }
          }
    }
  }

  private[this] def selectConstructorMethod(tpe: TypeNative): Option[MethodSymbNative] = ReflectionLock.synchronized {
    val constructor = findConstructor(tpe)
    if (!constructor.isTerm) {
      None
    } else {
      Some(constructor.asTerm.alternatives.head.asMethod)
    }
  }

  private[this] def selectNonImplicitParameters(symb: MethodSymbNative): List[List[SymbNative]] = ReflectionLock.synchronized {
    symb.paramLists.takeWhile(_.headOption.forall(!_.isImplicit))
  }

  override def isConcrete(tpe: TypeNative): Boolean = {
    tpe match {
      case _: u.u.RefinedTypeApi | u.u.definitions.AnyTpe     | u.u.definitions.AnyRefTpe
                                 | u.u.definitions.NothingTpe | u.u.definitions.NullTpe =>
        // 1. refinements never have a valid constructor unless they are tautological and can be substituted by a class
        // 2. ignoring non-runtime refinements (type members, covariant overrides) leads to unsoundness
        // rt.parents.size == 1 && !rt.decls.exists(_.isAbstract)
        false

      case _ =>
        tpe.typeSymbol.isClass && !tpe.typeSymbol.isAbstract && selectConstructorMethod(tpe).nonEmpty
    }
  }

  override def isWireableAbstract(tpe: TypeNative): Boolean = ReflectionLock.synchronized {
    val abstractMembers = tpe.members.filter(_.isAbstract)

    // no mistake here. Wireable astract is a abstract class or class with an abstract parent having all abstract members wireable
    tpe match {
      case rt: u.u.RefinedTypeApi =>
        val abstractMembers1 = (abstractMembers ++ rt.decls.filter(_.isAbstract)).toSet
        rt.parents.forall(!_.typeSymbol.isFinal) && abstractMembers1.forall(isWireableMethod)

      case t =>
        t.typeSymbol.isClass && t.typeSymbol.isAbstract && abstractMembers.forall(isWireableMethod)
    }
  }

  override def isFactory(tpe: TypeNative): Boolean = ReflectionLock.synchronized {
    val abstracts = tpe.members.filter(_.isAbstract)
    abstracts.exists(isFactoryMethod) &&
      abstracts.forall(m => isFactoryMethod(m) || isWireableMethod(m))
  }

  private[this] def isWireableMethod(decl: SymbNative): Boolean = ReflectionLock.synchronized {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && decl.asMethod.paramLists.isEmpty
  }

  private[this] def isFactoryMethod(decl: SymbNative): Boolean = ReflectionLock.synchronized {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && decl.asMethod.paramLists.nonEmpty
  }

  private[this] def findConstructor(tpe: TypeNative): SymbNative = {
    tpe.decl(u.u.termNames.CONSTRUCTOR)
  }

  protected def typeOfWithAnnotation: TypeNative
  protected def typeOfIdAnnotation: TypeNative
}

object ReflectionProviderDefaultImpl {
  def apply(macroUniverse: DIUniverse): ReflectionProvider.Aux[macroUniverse.type] = {
    new ReflectionProviderDefaultImpl {
      override final val u: macroUniverse.type = macroUniverse

      override protected val typeOfIdAnnotation: u.TypeNative = u.u.typeOf[Id]
      override protected val typeOfWithAnnotation: u.TypeNative = u.u.typeOf[With[Any]]
    }
  }
}

