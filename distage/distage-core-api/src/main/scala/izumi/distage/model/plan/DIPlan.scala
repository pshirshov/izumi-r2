package izumi.distage.model.plan

import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleBase
import izumi.distage.model.exceptions.DIBugException
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.repr.{DIPlanCompactFormatter, DepTreeRenderer}
import izumi.distage.model.plan.topology.DependencyGraph
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.functional.Renderable
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.{Toposort, ToposortLoopBreaker}
import izumi.fundamentals.graphs.{DG, GraphMeta}
import izumi.reflect.Tag

case class DIPlan(plan: DG[DIKey, ExecutableOp], input: PlannerInput) {
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

    def steps: List[ExecutableOp] = plan.plan.meta.nodes.values.toList

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
      val dg = new DependencyGraph(plan.plan.predecessors.links, DependencyGraph.DependencyKind.Depends)
      new DepTreeRenderer(dg.tree(key), plan.plan.meta.nodes).render()
    }

    def renderAllDeps(): String = {
      val effectiveRoots = plan.plan.noSuccessors
      effectiveRoots.map(renderDeps).mkString("\n")
    }
  }
}