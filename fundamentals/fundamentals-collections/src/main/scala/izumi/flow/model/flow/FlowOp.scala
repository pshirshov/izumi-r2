package izumi.flow.model.flow

import izumi.flow.model.expr.FExpr
import izumi.flow.model.values.FValue

sealed trait FlowOp {
  val inputs: List[ValueId]
  val output: ValueId
}

object FlowOp {
  sealed trait PipeOp extends FlowOp {
    val input: ValueId
    override val inputs: List[ValueId] = List(input)
  }

  sealed trait SourceOp extends FlowOp {
    override val inputs: List[ValueId] = List.empty
  }

  sealed trait MultiOp extends FlowOp

  case class FConstMulti(output: ValueId, value: List[FValue]) extends SourceOp

  case class FMap(input: ValueId, output: ValueId, expr: FExpr) extends PipeOp
  case class FFilter(input: ValueId, output: ValueId, expr: FExpr) extends PipeOp
  case class FFold(input: ValueId, output: ValueId, initial: FValue, expr: FExpr) extends PipeOp

  case class FZip(inputs: List[ValueId], output: ValueId) extends MultiOp
}
