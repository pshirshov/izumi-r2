package izumi.flow.model.flow

import izumi.fundamentals.graphs.struct.IncidenceMatrix

case class Flow(ops: List[FlowOp], outputs: List[ValueId]) {
  def dependencies: IncidenceMatrix[ValueId] = IncidenceMatrix(ops.map(op => (op.output, op.inputs.toSet)).toMap)
  def dependees: IncidenceMatrix[ValueId] = dependencies.transposed
  def index: Map[ValueId, FlowOp] = ops.map(op => (op.output, op)).toMap
}
