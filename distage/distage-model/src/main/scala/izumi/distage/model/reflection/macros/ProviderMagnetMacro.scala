package izumi.distage.model.reflection.macros

import izumi.distage.constructors.DebugProperties
import izumi.distage.model.providers.ProviderMagnet
import izumi.distage.model.reflection.universe.{RuntimeDIUniverse, StaticDIUniverse}
import izumi.distage.reflection.ReflectionProviderDefaultImpl
import izumi.fundamentals.reflection.{AnnotationTools, TrivialMacroLogger}

import scala.reflect.macros.blackbox

/**
  * To see macro debug output during compilation, set `-Dizumi.debug.macro.distage.providermagnet=true` java property! e.g.
  * {{{
  * sbt -Dizumi.debug.macro.distage.providermagnet=true compile
  * }}}
  */
// TODO: bench and optimize
class ProviderMagnetMacro(val c: blackbox.Context) {

  final val macroUniverse = StaticDIUniverse(c)

  private final val logger = TrivialMacroLogger.make[this.type](c, DebugProperties.`izumi.debug.macro.distage.providermagnet`)
  private final val reflectionProvider = ReflectionProviderDefaultImpl.Static(macroUniverse)

  import c.universe._
  import macroUniverse._

  case class ExtractedInfo(associations: List[Association.Parameter], isValReference: Boolean)

  def generateUnsafeWeakSafeTypes[R: c.WeakTypeTag](fun: c.Expr[_]): c.Expr[ProviderMagnet[R]] = implImpl[R](generateUnsafeWeakSafeTypes = true, fun.tree)
  def impl[R: c.WeakTypeTag](fun: c.Expr[_]): c.Expr[ProviderMagnet[R]] = implImpl[R](generateUnsafeWeakSafeTypes = false, fun.tree)

  def implImpl[R: c.WeakTypeTag](generateUnsafeWeakSafeTypes: Boolean, fun: c.Tree): c.Expr[ProviderMagnet[R]] = {
    val ret = SafeType(weakTypeOf[R])

    val tools =
      if (generateUnsafeWeakSafeTypes)
        DIUniverseLiftables.generateUnsafeWeakSafeTypes(macroUniverse)
      else
        DIUniverseLiftables(macroUniverse)

    import tools.{liftableParameter, liftableSafeType}

    val ExtractedInfo(associations, isValReference) = analyze(fun, ret)

    val casts = associations.zipWithIndex.map {
      case (st, i) =>
        st.tpe.use {
          t =>
            q"{ seqAny($i).asInstanceOf[$t] }"
        }
    }

    val result = c.Expr[ProviderMagnet[R]] {
      q"""{
        val fun = $fun

        val associations: ${typeOf[List[RuntimeDIUniverse.Association.Parameter]]} = $associations

        new ${weakTypeOf[ProviderMagnet[R]]}(
          new ${weakTypeOf[RuntimeDIUniverse.Provider.ProviderImpl[R]]}(
            associations
            , $ret
            , {
              seqAny =>
                _root_.scala.Predef.assert(seqAny.size == associations.size, "Impossible Happened! args list has different length than associations list")

                fun(..$casts)
              }
          )
        )
      }"""
    }

    logger.log(
      s"""DIKeyWrappedFunction info:
         | generateUnsafeWeakSafeTypes: $generateUnsafeWeakSafeTypes\n
         | Symbol: ${fun.symbol}\n
         | IsMethodSymbol: ${Option(fun.symbol).exists(_.isMethod)}\n
         | Extracted Annotations: ${associations.flatMap(_.context.symbol.annotations)}\n
         | Extracted DIKeys: ${associations.map(_.wireWith)}\n
         | IsValReference: $isValReference\n
         | argument: ${showCode(fun)}\n
         | argumentTree: ${showRaw(fun)}\n
         | argumentType: ${fun.tpe}
         | Result code: ${showCode(result.tree)}""".stripMargin
    )

    result
  }

  def analyze(tree: c.Tree, ret: SafeType): ExtractedInfo = tree match {
    case Block(List(), inner) =>
      analyze(inner, ret)
    case Function(args, body) =>
      analyzeMethodRef(args.map(_.symbol), body, ret)
    case _ if Option(tree.tpe).isDefined =>
      analyzeValRef(tree.tpe, ret)
    case _ =>
      c.abort(tree.pos
        ,
        s"""
           | Can handle only method references of form (method _) or lambda bodies of form (args => body).\n
           | Argument doesn't seem to be a method reference or a lambda:\n
           |   argument: ${showCode(tree)}\n
           |   argumentTree: ${showRaw(tree)}\n
           | Hint: Try appending _ to your method name""".stripMargin
      )
  }

  private def association(ret: SafeType)(p: SymbNative): Association.Parameter =
    reflectionProvider.associationFromParameter(SymbolInfo.Runtime(p, ret, p.typeSignature.typeSymbol.isParameter))

  def analyzeMethodRef(lambdaArgs: List[Symbol], body: Tree, ret: SafeType): ExtractedInfo = {
    val lambdaKeys: List[Association.Parameter] =
      lambdaArgs.map(association(ret))

    val methodReferenceKeys: List[Association.Parameter] = body match {
      case Apply(f, _) =>
        logger.log(s"Matched function body as a method reference - consists of a single call to a function $f - ${showRaw(body)}")

        val params = f.symbol.asMethod.typeSignature.paramLists.flatten
        params.map(association(ret))
      case _ =>
        logger.log(s"Function body didn't match as a variable or a method reference - ${showRaw(body)}")

        List()
    }

    logger.log(s"lambda keys: $lambdaKeys")
    logger.log(s"method ref keys: $methodReferenceKeys")

    val annotationsOnLambda: List[u.Annotation] = lambdaKeys.flatMap(_.context.symbol.annotations)
    val annotationsOnMethod: List[u.Annotation] = methodReferenceKeys.flatMap(_.context.symbol.annotations)

    val keys = if (
      methodReferenceKeys.size == lambdaKeys.size &&
        annotationsOnLambda.isEmpty && annotationsOnMethod.nonEmpty) {
      // Use types from the generated lambda, not the method reference, because method reference types maybe generic/unresolved
      //
      // (Besides, lambda types are the ones specified by the caller, we should always use them)
      methodReferenceKeys.zip(lambdaKeys).map {
        case (m, l) =>
          m.copy(tpe = l.tpe, wireWith = m.wireWith.withTpe(l.wireWith.tpe)) // gotcha: symbol not altered
      }
    } else {
      lambdaKeys
    }

    ExtractedInfo(keys, isValReference = false)
  }

  def analyzeValRef(sig: Type, ret: SafeType): ExtractedInfo = {
    val associations = sig.typeArgs.init.map {
      tpe =>
        val symbol = SymbolInfo.Static(
          c.freshName(tpe.typeSymbol.name.toString)
          , SafeType(tpe)
          , AnnotationTools.getAllTypeAnnotations(u)(tpe)
          , ret
          , tpe.typeSymbol.isTerm && tpe.typeSymbol.asTerm.isByNameParam
          , tpe.typeSymbol.isParameter
        )

        reflectionProvider.associationFromParameter(symbol)
    }

    ExtractedInfo(associations, isValReference = true)
  }

}
