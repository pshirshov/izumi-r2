package izumi.distage.model.planning

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.topology.PlanTopology
import izumi.distage.model.reflection.DIKey

trait PlanAnalyzer {
  @deprecated("This is not required anymore", "08/04/2021")
  def topology(plan: Iterable[ExecutableOp]): PlanTopology

  @deprecated("This is not required anymore", "08/04/2021")
  def topologyFwdRefs(plan: Iterable[ExecutableOp]): PlanTopology

  def requirements(op: ExecutableOp): Seq[(DIKey, Set[DIKey])]
}

