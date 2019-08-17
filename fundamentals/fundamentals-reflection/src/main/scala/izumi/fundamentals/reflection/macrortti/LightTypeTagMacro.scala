package izumi.fundamentals.reflection.macrortti

import java.util.concurrent.ConcurrentHashMap

import boopickle.{DefaultBasic, PickleImpl}
import izumi.fundamentals.platform.console.TrivialLogger
import izumi.fundamentals.reflection.TrivialMacroLogger
import izumi.fundamentals.reflection.macrortti.LightTypeTag.ReflectionLock
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef.RefinementDecl.TypeMember
import izumi.fundamentals.reflection.macrortti.LightTypeTagRef._

import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.api.Universe
import scala.reflect.macros.blackbox

final class LightTypeTagMacro(override val c: blackbox.Context) extends LightTypeTagMacro0[blackbox.Context](c)

class LightTypeTagMacro0[C <: blackbox.Context](override val c: C) extends LTTLiftables[C](c) {

  import c.universe._

  private val logger: TrivialLogger = TrivialMacroLogger.make[this.type](c, LightTypeTag.loggerId)

  def cacheEnabled: Boolean = c.settings.contains("ltt-cache")

  protected val impl = new LightTypeTagImpl[c.universe.type](c.universe, withCache = cacheEnabled, logger)

  @inline def makeWeakHKTag[ArgStruct: c.WeakTypeTag]: c.Expr[LTag.WeakHK[ArgStruct]] = {
    makeHKTagRaw[ArgStruct](weakTypeOf[ArgStruct])
  }

  @inline def makeWeakTag[T: c.WeakTypeTag]: c.Expr[LTag.Weak[T]] = {
    val res = makeFLTTImpl(weakTypeOf[T])
    c.Expr[LTag.Weak[T]](q"new ${weakTypeOf[LTag.Weak[T]]}($res)")
  }

  @inline def makeWeakTagCore[T: c.WeakTypeTag]: c.Expr[LightTypeTag] = {
    makeFLTTImpl(weakTypeOf[T])
  }

  @inline def makeWeakTag0[T: c.WeakTypeTag]: LightTypeTag = {
    impl.makeFLTT(weakTypeOf[T])
  }

  @inline def makeWeakTagString[T: c.WeakTypeTag]: c.Tree = {
    val res = impl.makeFLTT(weakTypeOf[T])
    val bytes = PickleImpl(res).toByteBuffer

    val str = new String(bytes.array(), "ISO-8859-1")

    q"$lightTypeTag.parseX(${res.ref}, $str : _root_.scala.String)"
  }

  def makeHKTagRaw[ArgStruct](argStruct: Type): c.Expr[LTag.WeakHK[ArgStruct]] = {
    def badShapeError(t: TypeApi) = {
      c.abort(c.enclosingPosition, s"Expected type shape RefinedType `{ type Arg[A] = X[A] }` for summoning `LightTagK[X]`, but got $t (raw: ${showRaw(t)} ${t.getClass})")
    }

    argStruct match {
      case r: RefinedTypeApi =>
        r.decl(TypeName("Arg")) match {
          case sym: TypeSymbolApi =>
            val res = makeFLTTImpl(sym.info.typeConstructor)
            // FIXME: `appliedType` doesn't work here for some reason; have to write down the entire name
            c.Expr[LTag.WeakHK[ArgStruct]](q"new _root_.izumi.fundamentals.reflection.macrortti.LTag.WeakHK[$argStruct]($res)")
          case _ => badShapeError(r)
        }
      case other => badShapeError(other)
    }
  }

  protected def makeFLTTImpl(tpe: Type): c.Expr[LightTypeTag] = {
    c.Expr[LightTypeTag](lifted_FLLT(impl.makeFullTagImpl(tpe)))
  }
}

// FIXME: Object makes this impossible to override ...
object LightTypeTagImpl {
  lazy val cache = new ConcurrentHashMap[Any, Any]()

  def makeLightTypeTag(u: Universe)(typeTag: u.Type): LightTypeTag = {
    ReflectionLock.synchronized {
      val logger = TrivialLogger.make[this.type](LightTypeTag.loggerId)
      new LightTypeTagImpl[u.type](u, withCache = false, logger).makeFullTagImpl(typeTag)
    }
  }
}

sealed trait Broken[T, S] {
  def toSet: Set[T]
}

object Broken {

  case class Single[T, S](t: T) extends Broken[T, S] {
    override def toSet: Set[T] = Set(t)
  }

  case class Compound[T, S](tpes: Set[T], decls: Set[S]) extends Broken[T, S] {
    override def toSet: Set[T] = tpes
  }

}


// FIXME: AnyVal makes this impossible to override ...
final class LightTypeTagImpl[U <: Universe with Singleton](val u: U, withCache: Boolean, logger: TrivialLogger) {

  import u._

  case class StableType(tpe: U#Type) {

    import izumi.fundamentals.reflection.ReflectionUtil._

    private final val dealiased: U#Type = {
      deannotate(tpe.dealias)
    }

    @inline private[this] final def freeTermPrefixTypeSuffixHeuristicEq(op: (U#Type, U#Type) => Boolean, t: U#Type, that: U#Type): Boolean =
      t -> that match {
        case (tRef: U#TypeRefApi, oRef: U#TypeRefApi) =>
          singletonFreeTermHeuristicEq(tRef.pre, oRef.pre) && (
            tRef.sym.isType && oRef.sym.isType && {
              val t1 = (u: U).internal.typeRef(u.NoType, tRef.sym, tRef.args)
              val t2 = (u: U).internal.typeRef(u.NoType, oRef.sym, oRef.args)

              op(t1, t2)
            }
              || tRef.sym.isTerm && oRef.sym.isTerm && tRef.sym == oRef.sym
            )
        case (tRef: U#SingleTypeApi, oRef: U#SingleTypeApi) =>
          singletonFreeTermHeuristicEq(tRef.pre, oRef.pre) && tRef.sym == oRef.sym
        case _ => false
      }

    private[this] final def singletonFreeTermHeuristicEq(t: U#Type, that: U#Type): Boolean =
      t.asInstanceOf[Any] -> that.asInstanceOf[Any] match {
        case (tpe: scala.reflect.internal.Types#UniqueSingleType, other: scala.reflect.internal.Types#UniqueSingleType)
          if tpe.sym.isFreeTerm && other.sym.isFreeTerm =>

          new StableType(tpe.pre.asInstanceOf[U#Type]) == new StableType(other.pre.asInstanceOf[U#Type]) && tpe.sym.name.toString == other.sym.name.toString
        case _ =>
          false
      }

    override final val hashCode: Int = {
      dealiased.typeSymbol.name.toString.hashCode
    }

    override final def equals(obj: Any): Boolean = {
      obj match {
        case that: StableType@unchecked =>
          dealiased =:= that.dealiased ||
            singletonFreeTermHeuristicEq(dealiased, that.dealiased) ||
            freeTermPrefixTypeSuffixHeuristicEq(_ =:= _, dealiased, that.dealiased)
        case _ =>
          false
      }
    }
  }

  @inline private[this] val any: Type = definitions.AnyTpe

  @inline private[this] val obj: Type = definitions.ObjectTpe

  @inline private[this] val nothing: Type = definitions.NothingTpe

  @inline private[this] val ignored: Set[Type] = Set(any, obj, nothing)

  @inline private[this] final val it = u.asInstanceOf[scala.reflect.internal.Types]
  @inline private[this] final val is = u.asInstanceOf[scala.reflect.internal.Symbols]

  def makeFullTagImpl(tpe: Type): LightTypeTag = {
    val out = makeRef(tpe)
    val inh = allTypeReferences(tpe)

    import izumi.fundamentals.collections.IzCollections._
    val inhUnrefined = inh
      .flatMap {
        t =>
          UniRefinement.breakRefinement(t).toSet
      }
    val inhdb = inhUnrefined
      .flatMap {
        i =>
          val tpef = i.dealias.resultType
          val targetNameRef = tpef.typeSymbol.fullName
          val prefix = toPrefix(tpef)
          val targetRef = NameReference(targetNameRef, prefix = prefix)

          val srcname = i match {
            case a: TypeRefApi =>
              val srcname = a.sym.fullName
              if (srcname != targetNameRef) {
                Seq((NameReference(srcname, prefix = toPrefix(i)), targetRef))
              } else {
                Seq.empty
              }

            case _ =>
              Seq.empty
          }

          val allbases = tpeBases(i)
            .filterNot(_.takesTypeArgs)
          srcname ++ allbases.map {
            b =>
              (targetRef, makeRef(b))
          }
      }
      .toMultimap
      .map {
        case (t, parents) =>
          t -> parents
            .collect {
              case r: AppliedNamedReference =>
                r.asName
            }
            .filterNot(_ == t)
      }
      .filterNot(_._2.isEmpty)

    val basesAsLambdas = makeBaseClasses(tpe, Some(out))
    val al = inhUnrefined.flatMap(a => makeBaseClasses(a, None))

    val basesdb: Map[AbstractReference, Set[AbstractReference]] = Seq(basesAsLambdas, al).flatten.toMultimap.filterNot(_._2.isEmpty)
    new LightTypeTag(out, () => basesdb, () => inhdb)
  }

  private def makeBaseClasses(t: Type, ref: Option[AbstractReference]): Seq[(AbstractReference, AbstractReference)] = {
    def baseLambdas(tpe: Type): Seq[AbstractReference] = {
      val basetypes = tpe.baseClasses.map(b => tpe.baseType(b)).filterNot(b => b.typeSymbol.fullName == tpe.typeSymbol.fullName)
      val targs = tpe.etaExpand.typeParams

      val lambdas = basetypes.flatMap {
        base =>
          if (targs.nonEmpty) {
            val lamParams = targs.zipWithIndex.map {
              case (p, idx) =>
                p.fullName -> LambdaParameter(idx.toString)
            }

            val reference = makeRef(base, lamParams.toMap)

            reference match {
              case l: Lambda =>
                Seq(l)
              case reference: AppliedReference =>
                Seq(Lambda(lamParams.map(_._2), reference))
                  .filter(_.allArgumentsReferenced)
            }
          } else {
            Seq.empty
          }
      }
      lambdas
    }

    val unref = UniRefinement.breakRefinement(t)

    unref
      .toSet
      .flatMap {
        r =>
          val t = if (r.takesTypeArgs) {
            r
          } else {
            r.etaExpand
          }

          val tref = ref.getOrElse(makeRef(t))

          if (t.takesTypeArgs) {
            baseLambdas(t)
              .collect {
                case l: Lambda =>
                  (tref, l)
              }
          } else {
            tpeBases(t)
              .map(b => makeRef(b))
              .filterNot(_ == tref).map(b => (tref, b))
          }
      }
      .toSeq
  }


  private def allTypeReferences(tpe: Type): Set[Type] = {
    def extract(tpe: Type, inh: mutable.HashSet[Type]): Unit = {
      val current = Seq(tpe, tpe.dealias.resultType)
      inh ++= current

      // we need to use tpe.etaExpand but 2.13 has a bug: https://github.com/scala/bug/issues/11673#
      val more = tpe.dealias.resultType.typeArgs.flatMap(_.dealias.resultType.typeSymbol.typeSignature match {
        case t: TypeBoundsApi =>
          Seq(t.hi, t.lo)
        case _ =>
          Seq.empty
      })

      val next = (tpe.typeArgs ++ tpe.dealias.resultType.typeArgs ++ more).filterNot(inh.contains)
      next.foreach {
        a =>
          extract(a, inh)
      }
    }

    val inh = mutable.HashSet[Type]()
    extract(tpe, inh)
    inh.toSet
  }

  private def tpeBases(tpe: Type): Seq[Type] = {
    val tpef = tpe.dealias.resultType
    val higherBases = tpef.baseClasses
    val parameterizedBases = higherBases
      .filterNot {
        s =>
          val btype = s.asType.toType
          ignored.exists(_ =:= btype) || btype =:= tpef
      }
      .map(s => tpef.baseType(s))
    val allbases = parameterizedBases
    allbases
  }


  private def makeRef(tpe: Type): AbstractReference = {
    val st = StableType(tpe)
    // we may accidentally recompute twice in concurrent environment but that's fine
    if (withCache) {
      Option(LightTypeTagImpl.cache.get(st)) match {
        case Some(value) =>
          value.asInstanceOf[AbstractReference]
        case None =>
          val ref = makeRef(tpe, Map.empty)
          LightTypeTagImpl.cache.put(st, ref)
          ref
      }
    } else {
      makeRef(tpe, Map.empty)
    }


  }

  private def makeRef(tpe: Type, terminalNames: Map[String, LambdaParameter]): AbstractReference = {
    makeRef(0)(tpe, Set(tpe), terminalNames)
  }

  private def makeRef(level: Int)(tpe: Type, path: Set[Type], terminalNames: Map[String, LambdaParameter]): AbstractReference = {
    val thisLevel = logger.sub(level)

    def sub(tpe: Type, stop: Map[String, LambdaParameter] = Map.empty): AbstractReference = {
      this.makeRef(level + 1)(tpe, path + tpe, terminalNames ++ stop)
    }

    def makeBoundaries(t: Type): Boundaries = {
      t.typeSymbol.typeSignature match {
        case b: TypeBoundsApi =>
          if ((b.lo =:= nothing && b.hi =:= any) || (path.contains(b.lo) || path.contains(b.hi))) {
            Boundaries.Empty
          } else {
            Boundaries.Defined(sub(b.lo), sub(b.hi))
          }
        case _ =>
          Boundaries.Empty
      }

    }

    def makeLambda(t: Type): AbstractReference = {
      val asPoly = t.etaExpand
      val result = asPoly.resultType.dealias
      val lamParams = t.typeParams.zipWithIndex.map {
        case (p, idx) =>
          p.fullName -> LambdaParameter(idx.toString)
      }

      thisLevel.log(s"✴️ λ type $t has parameters $lamParams, terminal names = $terminalNames")
      val reference = sub(result, lamParams.toMap)
      val out = Lambda(lamParams.map(_._2), reference)
      if (!out.allArgumentsReferenced) {
        thisLevel.err(s"⚠️ unused 𝝺 args! type $t => $out, context: $terminalNames, 𝝺 params: ${lamParams.map({ case (k, v) => s"$v = $k" })}, 𝝺 result: $result => $reference, referenced: ${out.referenced} ")
      }

      thisLevel.log(s"✳️ Restored $t => $out")
      out
    }

    def unpack(t: Type, rules: Map[String, LambdaParameter]): AppliedNamedReference = {
      val tpef = t.dealias.resultType
      val prefix = toPrefix(tpef)
      val typeSymbol = tpef.typeSymbol
      val b = makeBoundaries(tpef)
      val nameref = rules.get(typeSymbol.fullName) match {
        case Some(value) =>
          NameReference(value.name, b, prefix)

        case None =>
          NameReference(typeSymbol.fullName, b, prefix)
      }

      tpef.typeArgs match {
        case Nil =>
          nameref

        case args =>
          val params = args.zip(t.dealias.typeConstructor.typeParams).map {
            case (a, pa) =>
              TypeParam(sub(a), toVariance(pa.asType))
          }
          FullReference(nameref.ref, params, prefix)
      }
    }


    def unpackRefined(t: Type, rules: Map[String, LambdaParameter]): AppliedReference = {
      UniRefinement.breakRefinement(t) match {
        case Broken.Compound(tpes, decls) =>
          val parts = tpes.map(p => unpack(p, rules))
          val intersection = IntersectionReference(parts)

          if (decls.nonEmpty) {
            Refinement(intersection, UniRefinement.convertDecls(decls.toList, rules).toSet)
          } else {
            intersection
          }

        case _ =>
          // we intentionally ignore breakRefinement result here, it breaks lambdas
          unpack(t.dealias.resultType, rules)
      }
    }

    val out = tpe match {
      case _: PolyTypeApi =>
        makeLambda(tpe)
      case p if p.takesTypeArgs =>

        if (terminalNames.contains(p.typeSymbol.fullName)) {
          unpackRefined(p, terminalNames)
        } else {
          makeLambda(p)
        }

      case c =>
        unpackRefined(c, terminalNames)
    }

    out
  }

  object UniRefinement {
    def unapply(tpef: Type): Option[(List[Type], List[SymbolApi])] = {
      tpef.asInstanceOf[AnyRef] match {
        case x: it.RefinementTypeRef =>
          Some((x.parents.map(_.asInstanceOf[Type]), x.decls.map(_.asInstanceOf[SymbolApi]).toList))
        case r: RefinedTypeApi@unchecked =>
          Some((r.parents, r.decls.toList))
        case _ =>
          None
      }
    }

    def convertDecls(decls: List[SymbolApi], terminalNames: Map[String, LambdaParameter]): List[RefinementDecl] = {
      decls.flatMap {
        decl =>
          if (decl.isMethod) {
            val m = decl.asMethod
            val ret = m.returnType

            val params = m.paramLists.map {
              paramlist =>
                paramlist.map {
                  p =>
                    val pt = p.typeSignature
                    makeRef(pt, terminalNames).asInstanceOf[AppliedReference]
                }
            }

            val inputs = if (params.nonEmpty) {
              params
            } else {
              Seq(Seq.empty)
            }

            inputs.map {
              pl =>
                RefinementDecl.Signature(m.name.decodedName.toString, pl.toList, makeRef(ret, terminalNames).asInstanceOf[AppliedReference])
            }
          } else if (decl.isType) {
            val tpe = if (decl.isAbstract) {
              decl.asType.toType
            } else {
              decl.typeSignature
            }
            val ref = makeRef(tpe, terminalNames)
            Seq(TypeMember(decl.name.decodedName.toString, ref))
          } else {
            None
          }
      }
    }

    def breakRefinement(t: Type): Broken[Type, SymbolApi] = {
      breakRefinement0(t) match {
        case (t, d) if d.isEmpty && t.size == 1 =>
          Broken.Single(t.head)
        case (t, d) =>
          Broken.Compound(t, d)
      }
    }

    private def breakRefinement0(t: Type): (Set[Type], Set[SymbolApi]) = {
      fullDealias(t) match {
        case UniRefinement(parents, decls) =>
          val parts = parents.map(breakRefinement0)
          val types = parts.flatMap(_._1)
          val d = parts.flatMap(_._2)
          (types.toSet, (decls ++ d).toSet)
        case t =>
          (Set(t), Set.empty)

      }
    }
  }


  private def toPrefix(tpef: u.Type): Option[AppliedReference] = {
    def fromRef(o: Type): Option[AppliedReference] = {
      makeRef(o) match {
        case a: AppliedReference =>
          Some(a)
        case o =>
          throw new IllegalStateException(s"Cannot extract prefix from $tpef: expected applied reference, but got $o")
      }

    }

    val out = tpef match {
      case t: TypeRefApi =>
        t.pre match {
          case i if i.typeSymbol.isPackage =>
            None
          case k if k == it.NoPrefix =>
            None
          case k: ThisTypeApi =>
            k.sym.asType.toType match {
              case UniRefinement(_, _) =>
                None
              case o =>
                fromRef(o)
            }
          case o =>
            o.termSymbol match {
              case k if k == is.NoSymbol =>
                fromRef(o)
              case s =>
                val u = s.typeSignature
                if (u.typeSymbol.isAbstract) {

                  Some(NameReference(o.termSymbol.fullName))
                } else {
                  fromRef(u)
                }
            }
        }

      case _ =>
        None
    }

    out
  }

  private def fullDealias(t: u.Type): u.Type = {
    if (t.takesTypeArgs) {
      t.etaExpand.dealias.resultType.dealias.resultType
    } else {
      t.dealias.resultType
    }
  }

  private def toVariance(tpes: TypeSymbol): Variance = {
    if (tpes.isCovariant) {
      Variance.Covariant
    } else if (tpes.isContravariant) {
      Variance.Contravariant
    } else {
      Variance.Invariant
    }
  }
}

// simple materializers
object LTT {
  implicit def apply[T]: LightTypeTag = macro LightTypeTagMacro.makeWeakTagCore[T]
}

object `LTT[_]` {

  trait Fake

  implicit def apply[T[_]]: LightTypeTag = macro LightTypeTagMacro.makeWeakTagCore[T[Nothing]]
}

object `LTT[+_]` {

  trait Fake

  implicit def apply[T[+ _]]: LightTypeTag = macro LightTypeTagMacro.makeWeakTagCore[T[Nothing]]
}

object `LTT[A,B,_>:B<:A]` {
  implicit def apply[A, B <: A, T[_ >: B <: A]]: LightTypeTag = macro LightTypeTagMacro.makeWeakTagCore[T[Nothing]]
}

object `LTT[_[_]]` {

  trait Fake[F[_[_]]]

  implicit def apply[T[_[_]]]: LightTypeTag = macro LightTypeTagMacro.makeWeakTagCore[T[Nothing]]
}

object `LTT[_[_[_]]]` {

  trait Fake[F[_[_[_]]]]

  implicit def apply[T[_[_[_]]]]: LightTypeTag = macro LightTypeTagMacro.makeWeakTagCore[T[Nothing]]
}

object `LTT[_,_]` {

  trait Fake

  implicit def apply[T[_, _]]: LightTypeTag = macro LightTypeTagMacro.makeWeakTagCore[T[Nothing, Nothing]]
}

object `LTT[_[_],_[_]]` {

  trait Fake[_[_]]

  implicit def apply[T[_[_], _[_]]]: LightTypeTag = macro LightTypeTagMacro.makeWeakTagCore[T[Nothing, Nothing]]
}


object `LTT[_[_[_],_[_]]]` {

  trait Fake[K[_], V[_]]

  implicit def apply[T[_[_[_], _[_]]]]: LightTypeTag = macro LightTypeTagMacro.makeWeakTagCore[T[Nothing]]
}
