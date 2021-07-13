package izumi.flow.dsl

import izumi.flow.model.expr._
import izumi.flow.model.flow.FlowOp._
import izumi.flow.model.flow._
import izumi.flow.model.schema._
import izumi.flow.model.values._

object Test {
  val flow1 = Flow(
    List(
      FConstMulti(ValueId("ints_1", FType.FStream(FType.FInt)), Range(0, 10).map(idx => FValue.FVInt(idx)).toList),
      FFilter(
        ValueId("ints_1", FType.FStream(FType.FInt)),
        ValueId("ints_filtered", FType.FStream(FType.FInt)),
        FExpr.NashornOp("self % 2 == 0", FType.FInt, FType.FBool),
      ),
      FMap(
        ValueId("ints_filtered", FType.FStream(FType.FInt)),
        ValueId("ints_multiplied", FType.FStream(FType.FInt)),
        FExpr.NashornOp("self * 2", FType.FInt, FType.FInt),
      ),
    ),
    List(
      ValueId("ints_multiplied", FType.FStream(FType.FInt))
    ),
  )
  val flow2 = Flow(
    List(
      FConstMulti(ValueId("ints_1", FType.FStream(FType.FInt)), List(FValue.FVInt(1), FValue.FVInt(2), FValue.FVInt(3))),
      FConstMulti(ValueId("ints_2", FType.FStream(FType.FInt)), List(FValue.FVInt(6), FValue.FVInt(5), FValue.FVInt(4))),
      FZip(
        List(ValueId("ints_1", FType.FStream(FType.FInt)), ValueId("ints_2", FType.FStream(FType.FInt))),
        ValueId("ints_zipped", FType.FStream(FType.FTuple(List(FType.FInt, FType.FInt)))),
      ),
      FMap(ValueId("ints_1", FType.FStream(FType.FInt)), ValueId("ints_multiplied", FType.FStream(FType.FInt)), FExpr.NashornOp("self * 2", FType.FInt, FType.FInt)),
      FMap(
        ValueId("ints_zipped", FType.FStream(FType.FTuple(List(FType.FInt, FType.FInt)))),
        ValueId("ints_tuple_mapped", FType.FStream(FType.FTuple(List(FType.FInt, FType.FInt)))),
        FExpr.NashornOp("[self[0] + 10, self[1] * 2]", FType.FTuple(List(FType.FInt, FType.FInt)), FType.FTuple(List(FType.FInt, FType.FInt))),
      ),
      FFold(
        ValueId("ints_tuple_mapped", FType.FStream(FType.FTuple(List(FType.FInt, FType.FInt)))),
        ValueId("ints_sum", FType.FInt),
        FValue.FVInt(0),
        FExpr.NashornOp("self[0] + self[1][0] + self[1][1]", FType.FTuple(List(FType.FInt, FType.FInt)), FType.FInt),
      ),
    ),
    List(
      ValueId("ints_multiplied", FType.FStream(FType.FInt)),
      ValueId("ints_tuple_mapped", FType.FStream(FType.FTuple(List(FType.FInt, FType.FInt)))),
    ),
  )

  def main(args: Array[String]): Unit = {
//    new Simulation(flow1).run()

    new Simulation(flow2).run()
  }

}
