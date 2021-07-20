package izumi.distage.model.definition.dsl

import izumi.distage.constructors.macros.AnyConstructorMacro
import izumi.distage.model.definition.Binding.{EmptySetBinding, ImplBinding, SetElementBinding, SingletonBinding}
import izumi.distage.model.definition._
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetElementInstruction.ElementAddTags
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SetInstruction.{AddTagsAll, SetIdAll}
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.SingletonInstruction._
import izumi.distage.model.definition.dsl.AbstractBindingDefDSL.{SingletonRef, _}
import izumi.distage.model.exceptions.InvalidFunctoidModifier
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.DIKey
import izumi.distage.model.reflection.DIKey.SetKeyMeta
import izumi.fundamentals.platform.language.{CodePositionMaterializer, SourceFilePosition}
import izumi.reflect.Tag

import scala.collection.mutable
import scala.language.experimental.macros

trait AbstractBindingDefDSL[BindDSL[_], BindDSLAfterFrom[_], SetDSL[_]] {
  private[this] final val mutableState: mutable.ArrayBuffer[BindingRef] = _initialState
  protected[this] def _initialState: mutable.ArrayBuffer[BindingRef] = mutable.ArrayBuffer.empty

  private[definition] def _bindDSL[T](ref: SingletonRef): BindDSL[T]
  private[definition] def _bindDSLAfterFrom[T](ref: SingletonRef): BindDSLAfterFrom[T]
  private[definition] def _setDSL[T](ref: SetRef): SetDSL[T]

  private[definition] def frozenState: Iterator[Binding] = {
    mutableState.iterator.flatMap(_.interpret)
  }

  protected[this] def _registered[T <: BindingRef](bindingRef: T): T = {
    mutableState += bindingRef
    bindingRef
  }

  final protected[this] def make[T]: BindDSL[T] = macro AnyConstructorMacro.make[BindDSL, T]

  /**
    * Set bindings are useful for implementing event listeners, plugins, hooks, http routes, etc.
    *
    * To define a multibinding use `.many` and `.add` methods in ModuleDef DSL:
    *
    * {{{
    * import cats.effect._, org.http4s._, org.http4s.dsl.io._, scala.concurrent.ExecutionContext.Implicits.global
    * import distage._
    *
    * object HomeRouteModule extends ModuleDef {
    *   many[HttpRoutes[IO]].add {
    *     HttpRoutes.of[IO] { case GET -> Root / "home" => Ok(s"Home page!") }
    *   }
    * }
    * }}}
    *
    * Set bindings defined in different modules will be merged together into a single Set.
    * You can summon a created Set by type `Set[T]`:
    *
    * {{{
    * import cats.implicits._, org.http4s.server.blaze._, org.http4s.implicits._
    *
    * object BlogRouteModule extends ModuleDef {
    *   many[HttpRoutes[IO]].add {
    *     HttpRoutes.of[IO] { case GET -> Root / "blog" / post => Ok("Blog post ``$post''!") }
    *   }
    * }
    *
    * class HttpServer(routes: Set[HttpRoutes[IO]]) {
    *   val router = routes.foldK
    *
    *   def serve = BlazeBuilder[IO]
    *     .bindHttp(8080, "localhost")
    *     .mountService(router, "/")
    *     .start
    * }
    *
    * val objects = Injector().produce(HomeRouteModule ++ BlogRouteModule)
    * val server = objects.get[HttpServer]
    *
    * val testRouter = server.router.orNotFound
    *
    * testRouter.run(Request[IO](uri = uri("/home"))).flatMap(_.as[String]).unsafeRunSync
    * // Home page!
    *
    * testRouter.run(Request[IO](uri = uri("/blog/1"))).flatMap(_.as[String]).unsafeRunSync
    * // Blog post ``1''!
    * }}}
    *
    * @see Guice wiki on Multibindings: https://github.com/google/guice/wiki/Multibindings
    */
  final protected[this] def many[T](implicit tag: Tag[Set[T]], pos: CodePositionMaterializer): SetDSL[T] = {
    val setRef = _registered(new SetRef(Bindings.emptySet[T]))
    _setDSL(setRef)
  }

  /** Same as `make[T].from(implicitly[T])` * */
  final protected[this] def addImplicit[T: Tag](implicit instance: T, pos: CodePositionMaterializer): BindDSLAfterFrom[T] = {
    val ref = _registered(new SingletonRef(Bindings.binding(instance)))
    _bindDSLAfterFrom(ref)
  }

  /**
    * Create a dummy binding that throws an exception with an error message when it's created.
    *
    * Useful for prototyping.
    */
  final protected[this] def todo[T: Tag](implicit pos: CodePositionMaterializer): BindDSLAfterFrom[T] = {
    val ref = _registered(new SingletonRef(Bindings.todo(DIKey.get[T])(pos)))
    _bindDSLAfterFrom(ref)
  }

  /**
    * Modify a value bound at `T`. Modifiers stack and are all
    * applied before `T` is added to the object graph;
    * only the final instance is observable.
    *
    * {{{
    *   import distage.{Injector, ModuleDef}
    *
    *   Injector().produceGet[Int](new ModuleDef {
    *     make[Int].from(1)
    *     modify[Int](_ + 1)
    *     modify[Int](_ + 1)
    *   }).use(i => println(s"Got `Int` $i"))
    *   // Got `Int` 3
    * }}}
    *
    * You can also modify while summoning additional dependencies:
    *
    * {{{
    *   modify[Int]("named").by(_.flatAp {
    *     (adder: Adder, multiplier: Multiplier) =>
    *       int: Int =>
    *         multiplier.multiply(adder.add(int, 1), 10)
    *   })
    * }}}
    */
  final protected[this] def modify[T]: ModifyDSL[T, BindDSL, BindDSLAfterFrom, SetDSL] = new ModifyDSL[T, BindDSL, BindDSLAfterFrom, SetDSL](this)
  final private def _modify[T](key: DIKey.BasicKey)(f: Functoid[T] => Functoid[T])(implicit pos: CodePositionMaterializer): SingletonRef = {
    val (tpeKey: DIKey.TypeKey, maybeId) = key match {
      case tpeKey: DIKey.TypeKey => tpeKey -> None
      case idKey @ DIKey.IdKey(tpe, id, m) => DIKey.TypeKey(tpe, m) -> Some(Identifier.fromIdContract(id)(idKey.idContract))
    }
    val newProvider: Functoid[T] = f(Functoid.identityKey(key).asInstanceOf[Functoid[T]])
    val binding = SingletonBinding(tpeKey, ImplDef.ProviderImpl(newProvider.get.ret, newProvider.get), Set.empty, pos.get.position, isMutator = true)
    val ref = _registered(new SingletonRef(binding))
    maybeId.foreach(ref append SetId(_))
    ref
  }

  final protected[this] def _make[T: Tag](provider: Functoid[T])(implicit pos: CodePositionMaterializer): BindDSL[T] = {
    val ref = _registered(new SingletonRef(Bindings.provider[T](provider)))
    _bindDSL[T](ref)
  }
}

object AbstractBindingDefDSL {

  final class ModifyDSL[T, BindDSL[_], BindDSLAfterFrom[_], SetDSL[_]](private val dsl: AbstractBindingDefDSL[BindDSL, BindDSLAfterFrom, SetDSL]) extends AnyVal {
    def named(name: Identifier): ModifyNamedDSL[T, BindDSL, BindDSLAfterFrom, SetDSL] = {
      new ModifyNamedDSL(dsl, name)
    }

    def apply(f: T => T)(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.map(f))
    }

    def by(f: Functoid[T] => Functoid[T])(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      new ModifyTaggingDSL(dsl._modify(DIKey.get[T])(f)(pos))
    }

    def addDependency[B: Tag](implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependency(DIKey[B]))
    }

    def addDependency[B: Tag](name: Identifier)(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependency(DIKey[B](name)))
    }

    def addDependency(key: DIKey)(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependency(key))
    }

    def addDependencies(keys: Iterable[DIKey])(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependencies(keys))
    }
  }

  final class ModifyNamedDSL[T, BindDSL[_], BindDSLAfterFrom[_], SetDSL[_]](dsl: AbstractBindingDefDSL[BindDSL, BindDSLAfterFrom, SetDSL], name: Identifier) {
    def apply(f: T => T)(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.map(f))
    }

    def by(f: Functoid[T] => Functoid[T])(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      new ModifyTaggingDSL(dsl._modify(DIKey.get[T].named(name))(f)(pos))
    }

    def addDependency[B: Tag](implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependency(DIKey[B]))
    }

    def addDependency[B: Tag](name: Identifier)(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependency(DIKey[B](name)))
    }

    def addDependency(key: DIKey)(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependency(key))
    }

    def addDependencies(keys: Iterable[DIKey])(implicit tag: Tag[T], pos: CodePositionMaterializer): ModifyTaggingDSL[T] = {
      by(_.addDependencies(keys))
    }
  }

  final class ModifyTaggingDSL[T](private val ref: SingletonRef) extends AnyVal {
    def tagged(tags: BindingTag*): ModifyTaggingDSL[T] = {
      new ModifyTaggingDSL(ref.append(AddTags(tags.toSet)))
    }

    def by(f: Functoid[T] => Functoid[T]): ModifyTaggingDSL[T] = {
      new ModifyTaggingDSL[T](ref.append(Modify(f)))
    }

    def map(f: T => T): ModifyTaggingDSL[T] = {
      by(_.mapSame(f))
    }

    def addDependency[B: Tag]: ModifyTaggingDSL[T] = {
      by(_.addDependency(DIKey.get[B]))
    }

    def addDependency(key: DIKey): ModifyTaggingDSL[T] = {
      by(_.addDependency(key))
    }

    def addDependencies(keys: Iterable[DIKey]): ModifyTaggingDSL[T] = {
      by(_.addDependencies(keys))
    }
  }

  trait BindingRef {
    def interpret: collection.Seq[Binding]
  }

  final class SingletonRef(initial: SingletonBinding[DIKey.TypeKey], ops: mutable.Queue[SingletonInstruction] = mutable.Queue.empty) extends BindingRef {
    override def interpret: collection.Seq[ImplBinding] = {
      var b: SingletonBinding[DIKey.BasicKey] = initial
      var refs: List[SingletonBinding[DIKey.BasicKey]] = Nil
      val sortedOps = ops.sortBy {
        case _: SetImpl => 0
        case _: AddTags => 0
        case _: SetId => 0
        case _: Modify[_] => 1
        case _: SetIdFromImplName => 2
        case _: AliasTo => 3
      }
      sortedOps.foreach {
        case SetImpl(implDef) =>
          b = b.withImplDef(implDef)
        case AddTags(tags) =>
          b = b.addTags(tags)
        case SetId(contractedId) =>
          val key = DIKey.TypeKey(b.key.tpe).named(contractedId)
          b = b.withTarget(key)
        case SetIdFromImplName() =>
          b = b.withTarget(DIKey.IdKey(b.key.tpe, b.implementation.implType.tag.longName.toLowerCase))
        case Modify(functoidModifier) =>
          b.implementation match {
            case ImplDef.ProviderImpl(implType, function) =>
              val newProvider = functoidModifier(Functoid(function)).get
              if (newProvider.ret <:< implType) {
                b = b.withImplDef(ImplDef.ProviderImpl(implType, newProvider))
              } else {
                throw new InvalidFunctoidModifier(
                  s"Cannot apply invalid Functoid modifier $functoidModifier, new return type `${newProvider.ret}` is not a subtype of the old return type `${function.ret}`"
                )
              }
            case _ => ()
          }
        case AliasTo(key, pos) =>
          // it's ok to retrieve `tags`, `implType` & `key` from `b` because all changes to
          // `b` properties must come before first `aliasTo` operation in sorted ops set
          // when `aliased` is interpreted no more changes are going to happen
          val newRef = SingletonBinding(key, ImplDef.ReferenceImpl(b.implementation.implType, b.key, weak = false), b.tags, pos)
          refs = newRef :: refs
      }

      b :: refs.reverse
    }

    def key: DIKey.TypeKey = initial.key

    def append(op: SingletonInstruction): SingletonRef = {
      ops += op
      this
    }
  }

  final class SetRef(initial: EmptySetBinding[DIKey.TypeKey]) extends BindingRef {
    private[this] val setOps: mutable.Queue[SetInstruction] = mutable.Queue.empty
    private[this] val elems: mutable.Queue[SetElementRef] = mutable.Queue.empty
    private[this] val multiElems: mutable.Queue[MultiSetElementRef] = mutable.Queue.empty

    override def interpret: collection.Seq[Binding] = {
      val emptySetBinding = setOps.foldLeft(initial: EmptySetBinding[DIKey.BasicKey]) {
        (b, instr) =>
          instr match {
            case AddTagsAll(tags) => b.addTags(tags)
            case SetIdAll(id) => b.withTarget(DIKey.TypeKey(b.key.tpe).named(id))
          }
      }

      val finalKey = emptySetBinding.key

      val elemBindings = elems.map(_.interpret(finalKey))
      val multiSetBindings = multiElems.flatMap(_.interpret(finalKey))

      emptySetBinding +: elemBindings ++: multiSetBindings
    }

    def appendElem(op: SetElementRef): SetRef = {
      elems += op
      this
    }

    def appendOp(op: SetInstruction): SetRef = {
      setOps += op
      this
    }

    def appendMultiElem(op: MultiSetElementRef): SetRef = {
      multiElems += op
      this
    }
  }

  final class SetElementRef(implDef: ImplDef, pos: SourceFilePosition, ops: mutable.Queue[SetElementInstruction] = mutable.Queue.empty) {
    def interpret(setKey: DIKey.BasicKey): SetElementBinding = {
      val implKey = DIKey.TypeKey(implDef.implType)
      val elKey = DIKey.SetElementKey(setKey, implKey, SetKeyMeta.WithImpl(implDef))

      ops.foldLeft(SetElementBinding(elKey, implDef, Set.empty, pos)) {
        (b, instr) =>
          instr match {
            case ElementAddTags(tags) => b.addTags(tags)
          }
      }
    }

    def append(op: SetElementInstruction): SetElementRef = {
      ops += op
      this
    }
  }

  final class MultiSetElementRef(implDef: ImplDef, pos: SourceFilePosition) {
    private[this] val ops: mutable.Queue[MultiSetElementInstruction] = mutable.Queue.empty

    def interpret(setKey: DIKey.BasicKey): Seq[Binding] = {
      val valueProxyKey = DIKey.IdKey(implDef.implType, DIKey.MultiSetImplId(setKey, implDef))
      val valueProxyBinding = SingletonBinding(valueProxyKey, implDef, Set.empty, pos)

      val elementKey = DIKey.SetElementKey(setKey, valueProxyKey, SetKeyMeta.WithImpl(implDef))
      val refBind0 = SetElementBinding(elementKey, ImplDef.ReferenceImpl(valueProxyBinding.key.tpe, valueProxyBinding.key, weak = false), Set.empty, pos)

      val refBind = ops.foldLeft(refBind0) {
        (b, op) =>
          op match {
            case MultiSetElementInstruction.MultiAddTags(tags) => b.addTags(tags)
          }
      }

      Seq(valueProxyBinding, refBind)
    }

    def append(op: MultiSetElementInstruction): MultiSetElementRef = {
      ops += op
      this
    }
  }

  sealed trait SingletonInstruction
  object SingletonInstruction {
    final case class SetImpl(implDef: ImplDef) extends SingletonInstruction
    final case class AddTags(tags: Set[BindingTag]) extends SingletonInstruction
    final case class SetId(id: Identifier) extends SingletonInstruction
    final case class SetIdFromImplName() extends SingletonInstruction
    final case class Modify[T](functoidModifier: Functoid[T] => Functoid[T]) extends SingletonInstruction
    final case class AliasTo(key: DIKey.BasicKey, pos: SourceFilePosition) extends SingletonInstruction
  }

  sealed trait SetInstruction
  object SetInstruction {
    final case class AddTagsAll(tags: Set[BindingTag]) extends SetInstruction
    final case class SetIdAll(id: Identifier) extends SetInstruction
  }

  sealed trait SetElementInstruction
  object SetElementInstruction {
    final case class ElementAddTags(tags: Set[BindingTag]) extends SetElementInstruction
  }

  sealed trait MultiSetElementInstruction
  object MultiSetElementInstruction {
    final case class MultiAddTags(tags: Set[BindingTag]) extends MultiSetElementInstruction
  }

}
