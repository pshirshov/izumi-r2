package izumi.distage.model.definition.conflicts

import izumi.distage.model.planning.AxisPoint

final case class Node[N, V](deps: Set[N], meta: V)

final case class Annotated[N](key: N, mut: Option[Int], axis: Set[AxisPoint]) {
  def withoutAxis: MutSel[N] = MutSel(key, mut)
  def isMutator: Boolean = mut.isDefined
}

final case class MutSel[N](key: N, mut: Option[Int]) {
  def isMutator: Boolean = mut.isDefined
  def asString: String = s"$key${mut.fold("")(i => s":$i")}"
}
