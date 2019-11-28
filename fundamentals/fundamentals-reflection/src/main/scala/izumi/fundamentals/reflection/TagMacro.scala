package izumi.fundamentals.reflection

import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.reflection.ReflectionUtil.{Kind, kindOf}
import izumi.fundamentals.reflection.TagMacro._
import izumi.fundamentals.reflection.Tags.{HKTag, Tag}
import izumi.fundamentals.reflection.macrortti.{LightTypeTag, LightTypeTagMacro0}

import scala.annotation.implicitNotFound
import scala.collection.immutable.ListMap
import scala.reflect.api.Universe
import scala.reflect.macros.{TypecheckException, blackbox, whitebox}

// TODO: benchmark difference between running implicit search inside macro vs. return tree with recursive implicit macro expansion
// TODO: benchmark difference between searching all arguments vs. merge strategy
// TODO: benchmark ProviderMagnet vs. identity macro vs. normal function
class TagMacro(val c: blackbox.Context) {

  import c.universe._

  protected[this] val logger: TrivialLogger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.rtti`)
  private[this] val ltagMacro = new LightTypeTagMacro0[c.type](c)(logger)

  // workaround for a scalac bug - `Nothing` type is lost when two implicits for it are summoned from one implicit as in:
  //  implicit final def tagFromTypeTag[T](implicit t: TypeTag[T], l: LTag[T]): Tag[T] = Tag(t, l.fullLightTypeTag)
  // https://github.com/scala/bug/issues/11715
  def FIXMEgetLTagAlso[T: c.WeakTypeTag]: c.Expr[Tag[T]] = {
    val tpe = weakTypeOf[T]
    if (ReflectionUtil.allPartsStrong[c.universe.type](tpe.dealias)) {
      val ltag = ltagMacro.makeParsedLightTypeTagImpl(tpe)
      c.Expr[Tag[T]] {
        q"_root_.izumi.fundamentals.reflection.Tags.Tag.apply[$tpe]($ltag)"
      }
    } else {
      impl[T]
    }
  }

  def makeHKTag[ArgStruct: c.WeakTypeTag]: c.Expr[HKTag[ArgStruct]] = {
    val tpe = weakTypeOf[ArgStruct]
    if (ReflectionUtil.allPartsStrong[c.universe.type](tpe.dealias)) {
      val ltag = ltagMacro.makeParsedHKTagLightTypeTagImpl[ArgStruct](tpe)
      c.Expr[HKTag[ArgStruct]] {
        q"_root_.izumi.fundamentals.reflection.Tags.HKTag.apply($ltag)"
      }
    } else {
      c.abort(c.enclosingPosition, s"Can't materialize HKTag[$tpe]: found unresolved type parameters in $tpe")
    }
  }

  def impl[T: c.WeakTypeTag]: c.Expr[Tag[T]] = {
    logger.log(s"Got compile tag: ${weakTypeOf[T]}")

    if (getImplicitError().endsWith(":")) { // yep
      logger.log(s"Got continuation implicit error: ${getImplicitError()}")
    } else {
      resetImplicitError()
      addImplicitError("\n\n<trace>: ")
    }

    val tgt = ReflectionUtil.norm(c.universe: c.universe.type)(weakTypeOf[T].dealias)

    addImplicitError(s"  deriving Tag for $tgt:")

    val res = tgt match {
      case RefinedType(intersection, _) =>
        mkRefined[T](intersection, tgt)
      case _ =>
        mkTag[T](tgt)
    }

    addImplicitError(s"  succeeded for: $tgt")

    logger.log(s"Final code of TagMaterializer[${weakTypeOf[T]}]:\n ${showCode(res.tree)}")

    res
  }

  @inline
  protected[this] def mkRefined[T: c.WeakTypeTag](intersection: List[Type], originalRefinement: Type): c.Expr[Tag[T]] = {
    val intersectionsTags = c.Expr[List[LightTypeTag]](q"${
      intersection.map {
        t0 =>
          val t = ReflectionUtil.norm(c.universe: c.universe.type)(t0.dealias)
          summonTypeTagFromTag(t)
      }
    }")
    val structTag = mkStruct(originalRefinement)

    reify {
      {Tag.refinedTag[T](intersectionsTags.splice, structTag.splice)}
    }
  }

  @inline
  // have to tag along the original intersection, because scalac dies on trying to summon typetag for a custom made refinedType from `internal.refinedType` ...
  protected[this] def mkStruct(originalRefinement: Type): c.Expr[LightTypeTag] = {

    originalRefinement.decls.find(_.info.typeSymbol.isParameter).foreach {
      s =>
        val msg = s"  Encountered a type parameter ${s.info} as a part of structural refinement of $originalRefinement: It's not yet supported to summon a Tag for ${s.info} in that position!"

        addImplicitError(msg)
        c.abort(s.pos, getImplicitError())
    }

    // TODO: walk over members of struct and retrieve type tags for them
    ltagMacro.makeParsedLightTypeTagImpl(originalRefinement)
  }

  // we need to handle four cases – type args, refined types, type bounds and bounded wildcards(? check existence)
  @inline
  protected[this] def mkTag[T: c.WeakTypeTag](tpe: c.Type): c.Expr[Tag[T]] = {
    val constructorTag = {
      val ctor = tpe.typeConstructor
      getCtorKindIfCtorIsTypeParameter(ctor) match {
        // type constructor of this type is not a type parameter
        // but some of its arguments are, we should resolve the arguments
        case None =>
          ltagMacro.makeParsedLightTypeTagImpl(ctor)
        // error: the entire type is just a type parameter
        case Some(Kind(Nil)) =>
          val msg = s"  could not find implicit value for ${hktagFormat(tpe)}: $tpe is a type parameter without an implicit Tag or TypeTag!"
          addImplicitError(msg)
          c.abort(c.enclosingPosition, getImplicitError())
        // type constructor is a type parameter
        case Some(kind) =>
          summonTypeTagFromTagWithKind(ctor, kind)
      }
    }
    val argTags = {
      val args = tpe.typeArgs.map(t => ReflectionUtil.norm(c.universe: c.universe.type)(t.dealias))
      c.Expr[List[LightTypeTag]](q"${args.map(summonTypeTagFromTag)}")
    }

    reify {
      {Tag.appliedTag[T](constructorTag.splice, argTags.splice)}
    }
  }

  @inline
  protected[this] def getCtorKindIfCtorIsTypeParameter(tpe: c.Type): Option[Kind] = {
    // c.internal.isFreeType ?
    if (tpe.typeSymbol.isParameter)
      Some(kindOf(tpe))
    else
      None
  }

  protected[this] def mkTypeParameter(owner: Symbol, kind: Kind): Symbol = {
    import internal.reificationSupport._
    import internal.{polyType, typeBounds}

    val tpeSymbol = newNestedSymbol(owner, freshTypeName(""), NoPosition, Flag.PARAM | Flag.DEFERRED, isClass = false)

    val tpeTpe = if (kind.args.nonEmpty) {
      val params = kind.args.map(mkTypeParameter(tpeSymbol, _))

      polyType(params, typeBounds(definitions.NothingTpe, definitions.AnyTpe))
    } else {
      typeBounds(definitions.NothingTpe, definitions.AnyTpe)
    }

    setInfo(tpeSymbol, tpeTpe)

    tpeSymbol
  }

  @inline
  protected[this] def mkHKTagArg(tpe: c.Type, kind: Kind): Type = {

    import internal.reificationSupport._

    val staticOwner = c.prefix.tree.symbol.owner

    logger.log(s"staticOwner: $staticOwner")

    val parents = List(definitions.AnyRefTpe)
    val mutRefinementSymbol: Symbol = newNestedSymbol(staticOwner, TypeName("<refinement>"), NoPosition, FlagsRepr(0L), isClass = true)

    val mutArg: Symbol = newNestedSymbol(mutRefinementSymbol, TypeName("Arg"), NoPosition, FlagsRepr(0L), isClass = false)
    val params = kind.args.map(mkTypeParameter(mutArg, _))
    setInfo(mutArg, mkPolyType(tpe, params))

    val scope = newScopeWith(mutArg)

    setInfo[Symbol](mutRefinementSymbol, RefinedType(parents, scope, mutRefinementSymbol))

    RefinedType(parents, scope, mutRefinementSymbol)
  }

  @inline
  protected[this] def mkPolyType(tpe: c.Type, params: List[c.Symbol]): Type = {
    val rhsParams = params.map(internal.typeRef(NoPrefix, _, Nil))

    internal.polyType(params, appliedType(tpe, rhsParams))
  }

  @inline
  protected[this] def summonTypeTagFromTag(tpe: c.Type): c.Expr[LightTypeTag] = {
    summonTypeTagFromTagWithKind(tpe, kindOf(tpe))
  }

  @inline
  protected[this] def summonTypeTagFromTagWithKind(tpe: c.Type, kind: Kind): c.Expr[LightTypeTag] = {
    // dynamically typed
    val summoned = {
      try {
        if (kind == Kind(Nil)) {
          c.inferImplicitValue(appliedType(weakTypeOf[Tag[Nothing]].typeConstructor, tpe), silent = false)
        } else {
          val Arg = mkHKTagArg(tpe, kind)
          logger.log(s"Created impicit Arg: $Arg")
          c.inferImplicitValue(appliedType(weakTypeOf[HKTag[Nothing]].typeConstructor, Arg), silent = false)
        }
      } catch {
        case _: TypecheckException =>
          val msg =
            s"""  could not find implicit value for ${hktagFormat(tpe)}
               |${
              hktagFormatMap.get(kind) match {
                case Some(_) => ""
                case None =>
                  val (args, params) = kind.args.zipWithIndex.map {
                    case (k, i) =>
                      val name = s"T${i + 1}"
                      k.format(name) -> name
                  }.unzip
                  s"""\n$tpe is of a kind $kind, which doesn't have a tag name. Please create a tag synonym as follows:\n\n
                     |  type TagXXX[${kind.format("K")}] = HKTag[ { type Arg[${args.mkString(", ")}] = K[${params.mkString(", ")}] } ]\n\n
                     |And use it in your context bound, as in def x[$tpe: TagXXX] = ...
                     |OR use Tag.auto.T macro, as in def x[$tpe: Tag.auto.T] = ...
               """.stripMargin
              }
            }""".stripMargin
          addImplicitError(msg)
          c.abort(c.enclosingPosition, getImplicitError())
      }
    }

    c.Expr[LightTypeTag](q"{$summoned.tag}")
  }

  def getImplicitError(): String = {
    val annotations = symbolOf[Tag[Any]].annotations
    annotations.headOption.flatMap(
      AnnotationTools.findArgument(_) {
        case Literal(Constant(s: String)) => s
      }
    ).getOrElse(defaultTagImplicitError)
  }

  @inline
  protected[this] def addImplicitError(err: String): Unit = {
    setImplicitError(s"${getImplicitError()}\n$err")
  }

  @inline
  protected[this] def resetImplicitError(): Unit = {
    setImplicitError(defaultTagImplicitError)
  }

  @inline
  protected[this] def setImplicitError(err: String): Unit = {
    import internal.decorators._

    symbolOf[Tag[Any]].setAnnotations(
      Annotation(typeOf[implicitNotFound], List[Tree](Literal(Constant(err))), ListMap.empty)
    )
    ()
  }

//  // TODO: in 2.13 we can use these little functions to enrich error messages further (possibly remove .setAnnotation hack completely) by attaching implicitNotFound to parameter
//  c.Expr[ScalaReflectTypeTag[_]](q"""
//     { def $name(implicit @_root_.scala.annotation.implicitNotFound($implicitMsg)
//    $param: ${appliedType(weakTypeOf[Tag[Nothing]], t)}) = $param; $name.tag }""")

}

private object TagMacro {
  final val defaultTagImplicitError =
    "could not find implicit value for Tag[${T}]. Did you forget to put on a Tag, TagK or TagKK context bound on one of the parameters in ${T}? e.g. def x[T: Tag, F[_]: TagK] = ..."

  final def hktagFormatMap: Map[Kind, String] = {
    Map(
      Kind(Nil) -> "Tag",
      Kind(Kind(Nil) :: Nil) -> "TagK",
      Kind(Kind(Nil) :: Kind(Nil) :: Nil) -> "TagKK",
      Kind(Kind(Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> "TagK3",
      Kind(Kind(Kind(Nil) :: Nil) :: Nil) -> "TagT",
      Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Nil) -> "TagTK",
      Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> "TagTKK",
      Kind(Kind(Kind(Nil) :: Nil) :: Kind(Nil) :: Kind(Nil) :: Kind(Nil) :: Nil) -> "TagTK3",
    )
  }

  final def hktagFormat(tpe: Universe#Type): String = {
    val kind = kindOf(tpe)
    hktagFormatMap.get(kind) match {
      case Some(t) => s"$t[$tpe]"
      case _ => s"HKTag for $tpe of kind $kind"
    }
  }
}

class TagLambdaMacro(override val c: whitebox.Context) extends TagMacro(c) {

  import c.universe._
  import c.universe.internal.decorators._

  def lambdaImpl: c.Tree = {
    val pos = c.macroApplication.pos

    val targetTpe = c.enclosingUnit.body.collect {
      case AppliedTypeTree(t, arg :: _) if t.exists(_.pos == pos) =>
        c.typecheck(arg, c.TYPEmode, c.universe.definitions.NothingTpe, silent = false, withImplicitViewsDisabled = true, withMacrosDisabled = true)
          .tpe
    }.headOption match {
      case None =>
        c.abort(c.enclosingPosition, "Couldn't find an the type that `Tag.auto.T` macro was applied to, please make sure you use the correct syntax, as in `def tagk[F[_]: Tag.auto.T]: TagK[T] = implicitly[Tag.auto.T[F]]`")
      case Some(t) =>
        t
    }

    val kind = kindOf(targetTpe)

    logger.log(s"Found posiition $pos, target type $targetTpe, target kind $kind")

    val ctorParam = mkTypeParameter(NoSymbol, kind)
    val argStruct = mkHKTagArg(ctorParam.asType.toType, kind)

    val resultType = c.typecheck(
      tq"{ type T[${c.internal.typeDef(ctorParam)}] = _root_.izumi.fundamentals.reflection.Tags.HKTag[$argStruct] }"
      , c.TYPEmode, c.universe.definitions.NothingTpe, silent = false, withImplicitViewsDisabled = true, withMacrosDisabled = true
    ).tpe

    val res = Literal(Constant(())).setType(resultType)

    logger.log(s"final result: $resultType")

    res
  }
}
