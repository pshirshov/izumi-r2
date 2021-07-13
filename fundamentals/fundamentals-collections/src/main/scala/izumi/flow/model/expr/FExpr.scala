package izumi.flow.model.expr

import izumi.flow.model.schema.FType

sealed trait FExpr {
  //val closure: List[ValueId]
  val output: FType
}

object FExpr {
  case class NashornOp(expr: String, input: FType, output: FType) extends FExpr
}
