package izumi.distage.planning

import izumi.distage.model.plan.ExecutableOp.ProxyOp.{InitProxy, MakeProxy}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, MonadicOp, WiringOp}
import izumi.distage.model.plan._
import izumi.distage.model.plan.topology.DependencyGraph.DependencyKind
import izumi.distage.model.plan.topology.PlanTopology.PlanTopologyImmutable
import izumi.distage.model.plan.topology.{DependencyGraph, PlanTopology}
import izumi.distage.model.planning.PlanAnalyzer
import izumi.distage.model.reflection._
import izumi.fundamentals.graphs.struct.IncidenceMatrix

import scala.annotation.nowarn
import scala.collection.mutable

@nowarn("msg=Unused import")
class PlanAnalyzerDefaultImpl extends PlanAnalyzer {

  import scala.collection.compat._

  def topology(plan: Iterable[ExecutableOp]): PlanTopology = {
    computeTopology(
      plan,
      refFilter = _ => _ => false,
      postFilter = _ => true,
    )
  }

  def topologyFwdRefs(plan: Iterable[ExecutableOp]): PlanTopology = {
    computeTopology(
      plan,
      refFilter = acc => key => acc.contains(key),
      postFilter = _._2.nonEmpty,
    )
  }

  def requirements(op: ExecutableOp): Seq[(DIKey, Set[DIKey])] = {
    op match {
      case w: WiringOp =>
        Seq((op.target, w.wiring.requiredKeys))

      case w: MonadicOp =>
        Seq((op.target, Set(w.effectKey)))

      case c: CreateSet =>
        Seq((op.target, c.members))

      case _: MakeProxy =>
        Seq((op.target, Set.empty))

      case _: ImportDependency =>
        Seq((op.target, Set.empty))

      case i: InitProxy =>
        Seq((i.target, Set(i.proxy.target))) ++ requirements(i.proxy.op)
    }
  }

  private type Accumulator = mutable.HashMap[DIKey, mutable.Set[DIKey]]

  private type RefFilter = Accumulator => DIKey => Boolean

  private type PostFilter = ((DIKey, mutable.Set[DIKey])) => Boolean

  private def computeTopology(plan: Iterable[ExecutableOp], refFilter: RefFilter, postFilter: PostFilter): PlanTopology = {
    val dependencies = plan
      .foldLeft(new Accumulator) {
        case (acc, op) =>
          requirements(op)
            .map {
              case (k, r) =>
                (k, r.filterNot(refFilter(acc)))
            } // it's important NOT to update acc before we computed deps
            .foreach {
              case (k, r) =>
                acc.getOrElseUpdate(k, mutable.Set.empty) ++= r
            }

          acc
      }
      .view
      .filter(postFilter)
      .mapValues(_.toSet)
      .toMap

    val dependants = reverseReftable(dependencies)
    PlanTopologyImmutable(DependencyGraph(IncidenceMatrix(dependants), DependencyKind.Required), DependencyGraph(IncidenceMatrix(dependencies), DependencyKind.Depends))
  }

  @nowarn("msg=deprecated")
  private def reverseReftable(dependencies: Map[DIKey, Set[DIKey]]): Map[DIKey, Set[DIKey]] = {
    val dependants = dependencies.foldLeft(new Accumulator with mutable.MultiMap[DIKey, DIKey]) {
      case (acc, (reference, referencee)) =>
        acc.getOrElseUpdate(reference, mutable.Set.empty[DIKey])
        referencee.foreach(acc.addBinding(_, reference))
        acc
    }
    dependants.view.mapValues(_.toSet).toMap
  }
}
