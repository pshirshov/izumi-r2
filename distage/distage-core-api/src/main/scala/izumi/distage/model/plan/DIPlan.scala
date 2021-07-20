package izumi.distage.model.plan

import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions.{DIBugException, IncompatibleEffectTypesException, InvalidPlanException, MissingInstanceException}
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, MonadicOp}
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.repr.{DIPlanCompactFormatter, DepTreeRenderer}
import izumi.distage.model.plan.topology.DependencyGraph
import izumi.distage.model.recursive.LocatorRef
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.functional.Renderable
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.{Toposort, ToposortLoopBreaker}
import izumi.fundamentals.graphs.{DG, GraphMeta}
import izumi.reflect.{Tag, TagK}

final case class DIPlan(
  plan: DG[DIKey, ExecutableOp],
  input: PlannerInput,
) {
  // TODO: equals/hashcode should not be used under normal circumstances. Currently we need them for "memoization levels" to work but we have to get rid of that
  override def hashCode(): Int = {
    this.plan.meta.hashCode() ^ this.plan.predecessors.hashCode()
  }

  override def equals(obj: Any): Boolean = obj match {
    case p: DIPlan =>
      this.plan.meta == p.plan.meta && this.plan.predecessors == p.plan.predecessors
    case _ => false
  }
}

object DIPlan {
  def empty: DIPlan = DIPlan(
    DG(IncidenceMatrix.empty, IncidenceMatrix.empty, GraphMeta.empty),
    PlannerInput.everything(ModuleBase.empty),
  )

  @inline implicit final def defaultFormatter: Renderable[DIPlan] = DIPlanCompactFormatter

  implicit final class DIPlanSyntax(private val plan: DIPlan) extends AnyVal {
    def keys: Set[DIKey] = plan.plan.meta.nodes.keySet

    @deprecated("should be removed with OrderedPlan (returned steps are no longer ordered)", "13/04/2021")
    def steps: Iterable[ExecutableOp] = plan.plan.meta.nodes.values

    def toposort: Seq[DIKey] = {
      Toposort.cycleBreaking(plan.plan.predecessors, ToposortLoopBreaker.breakOn[DIKey](_.headOption)) match {
        case Left(value) =>
          throw DIBugException(s"BUG: toposort failed during plan rendering: $value")
        case Right(value) =>
          value
      }
    }

    def replaceWithImports(keys: Set[DIKey]): DIPlan = {
      val imports = keys.flatMap {
        k =>
          val dependees = plan.plan.successors.links(k)
          val dependeesWithoutKeys = dependees.diff(keys)
          if (dependeesWithoutKeys.nonEmpty || plan.plan.noSuccessors.contains(k)) {
            Seq((k, ImportDependency(k, dependeesWithoutKeys, plan.plan.meta.nodes(k).origin.value.toSynthetic)))
          } else {
            Seq.empty
          }
      }

      val replaced = imports.toMap
      val removed = keys -- replaced.keySet

      val s = IncidenceMatrix(plan.plan.predecessors.without(removed).links ++ replaced.keys.map(k => (k, Set.empty[DIKey])))
      val m = GraphMeta(plan.plan.meta.without(removed).nodes ++ replaced)
      DIPlan(DG(s.transposed, s, m), plan.input)
    }

    @deprecated("should be removed with OrderedPlan", "13/04/2021")
    def definition: ModuleBase = {
      val userBindings = steps.flatMap {
        op =>
          op.origin.value match {
            case OperationOrigin.UserBinding(binding) =>
              Seq(binding)
            case _ =>
              Seq.empty
          }
      }.toSet
      ModuleBase.make(userBindings)
    }

    private def collectChildrenKeys[T: Tag]: Set[DIKey] = {
      val tpe = SafeType.get[T]
      steps.iterator.collect {
        case op if op.instanceType <:< tpe => op.target
      }.toSet
    }

    @deprecated("should be removed with OrderedPlan", "13/04/2021")
    def collectChildrenKeysSplit[T1, T2](implicit t1: Tag[T1], t2: Tag[T2]): (Set[DIKey], Set[DIKey]) = {
      if (t1.tag == t2.tag) {
        (collectChildrenKeys[T1], Set.empty)
      } else {
        val tpe1 = SafeType.get[T1]
        val tpe2 = SafeType.get[T2]

        val res1 = Set.newBuilder[DIKey]
        val res2 = Set.newBuilder[DIKey]

        steps.foreach {
          op =>
            if (op.instanceType <:< tpe1) {
              res1 += op.target
            } else if (op.instanceType <:< tpe2) {
              res2 += op.target
            }
        }
        (res1.result(), res2.result())
      }
    }

    def render()(implicit ev: Renderable[DIPlan]): String = ev.render(plan)

    def renderDeps(key: DIKey): String = {
      val dg = new DependencyGraph(plan.plan.predecessors, DependencyGraph.DependencyKind.Depends)
      new DepTreeRenderer(dg.tree(key), plan.plan.meta.nodes).render()
    }

    def renderDependees(key: DIKey): String = {
      val dg = new DependencyGraph(plan.plan.successors, DependencyGraph.DependencyKind.Required)
      new DepTreeRenderer(dg.tree(key), plan.plan.meta.nodes).render()
    }

    def renderAllDeps(): String = {
      val effectiveRoots = plan.plan.noSuccessors
      effectiveRoots.map(renderDeps).mkString("\n")
    }
  }

  implicit final class DIPlanAssertions(private val plan: DIPlan) extends AnyVal {

    @deprecated("Use distage.Injector#verify", "20.07.2021")
    /**
      * Check for any unresolved dependencies,
      * or for any `make[_].fromEffect` or `make[_].fromResource` bindings that are incompatible with the passed `F`,
      * or for any other issue that would cause [[izumi.distage.model.Injector#produce Injector.produce]] to fail
      *
      * If this returns `F.unit` then the wiring is generally correct,
      * modulo runtime exceptions in user code,
      * and `Injector.produce` should succeed.
      *
      * However, presence of imports does not *always* mean
      * that a plan is invalid, imports may be fulfilled by a parent
      * `Locator`, by BootstrapContext, or they may be materialized by
      * a custom [[izumi.distage.model.provisioning.strategies.ImportStrategy]]
      *
      * An effect is compatible if it's a subtype of `F` or is a type equivalent to [[izumi.fundamentals.platform.functional.Identity]] (e.g. `cats.Id`)
      *
      * Will `F.fail` the effect with [[izumi.distage.model.exceptions.InvalidPlanException]] if there are issues.
      *
      * @tparam F effect type to check against
      */
    final def assertValid[F[_]: QuasiIO: TagK](ignoredImports: DIKey => Boolean = Set.empty): F[Unit] = {
      isValid(ignoredImports).fold(QuasiIO[F].unit)(QuasiIO[F].fail(_))
    }

    @deprecated("Use distage.Injector#verify", "20.07.2021")
    /**
      * Same as [[assertValid]], but throws an [[izumi.distage.model.exceptions.InvalidPlanException]] if there are unresolved imports
      *
      * @throws izumi.distage.model.exceptions.InvalidPlanException if there are issues
      */
    final def assertValidOrThrow[F[_]: TagK](ignoredImports: DIKey => Boolean = Set.empty): Unit = {
      isValid(ignoredImports).fold(())(throw _)
    }

    @deprecated("Use distage.Injector#verify", "20.07.2021")
    /** Same as [[unresolvedImports]], but returns a pretty-printed exception if there are unresolved imports */
    final def isValid[F[_]: TagK](ignoredImports: DIKey => Boolean = Set.empty): Option[InvalidPlanException] = {
      import izumi.fundamentals.platform.strings.IzString._
      val unresolved = unresolvedImports(ignoredImports).fromNonEmptyList.map(op => MissingInstanceException.format(op.target, op.references))
      val effects = incompatibleEffectType[F].fromNonEmptyList.map(op => IncompatibleEffectTypesException.format(op, SafeType.getK[F], op.effectHKTypeCtor))
      for {
        allErrors <- NonEmptyList.from(unresolved ++ effects)
      } yield new InvalidPlanException(allErrors.toList.niceList(shift = ""))
    }

    /**
      * Check for any unresolved dependencies.
      *
      * If this returns `None` then the wiring is generally correct,
      * modulo runtime exceptions in user code,
      * and `Injector.produce` should succeed.
      *
      * However, presence of imports does not *always* mean
      * that a plan is invalid, imports may be fulfilled by a parent
      * `Locator`, by BootstrapContext, or they may be materialized by
      * a custom [[izumi.distage.model.provisioning.strategies.ImportStrategy]]
      *
      * @return a non-empty list of unresolved imports if present
      */
    final def unresolvedImports(ignoredImports: DIKey => Boolean = Set.empty): Option[NonEmptyList[ImportDependency]] = {
      val locatorRefKey = DIKey[LocatorRef]
      val nonMagicImports = plan.steps.iterator.collect {
        case i: ImportDependency if i.target != locatorRefKey && !ignoredImports(i.target) => i
      }.toList
      NonEmptyList.from(nonMagicImports)
    }
    final def unresolvedImports: Option[NonEmptyList[ImportDependency]] = unresolvedImports()

    /**
      * Check for any `make[_].fromEffect` or `make[_].fromResource` bindings that are incompatible with the passed `F`.
      *
      * An effect is compatible if it's a subtype of `F` or is a type equivalent to [[izumi.fundamentals.platform.functional.Identity]] (e.g. `cats.Id`)
      *
      * @tparam F effect type to check against
      * @return a non-empty list of operations incompatible with `F` if present
      */
    final def incompatibleEffectType[F[_]: TagK]: Option[NonEmptyList[MonadicOp]] = {
      val effectType = SafeType.getK[F]
      val badSteps = plan.steps.iterator.collect {
        case op: MonadicOp if op.effectHKTypeCtor != SafeType.identityEffectType && !(op.effectHKTypeCtor <:< effectType) => op
      }.toList
      NonEmptyList.from(badSteps)
    }

  }
}
