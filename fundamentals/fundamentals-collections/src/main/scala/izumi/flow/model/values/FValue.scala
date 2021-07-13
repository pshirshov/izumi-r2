package izumi.flow.model.values

import izumi.flow.model.schema.FType
import izumi.flow.model.schema.FType.{FRecord, FTuple}

sealed trait FValue {
  def tpe: FType
//  def value: Any
//  def valueRef: AnyRef = value.asInstanceOf[AnyRef]
}

object FValue {
  sealed trait FVBuiltin extends FValue { def tpe: FType.FBuiltin }
  case class FVString(value: String) extends FVBuiltin { val tpe: FType.FBuiltin = FType.FString }
  case class FVInt(value: Int) extends FVBuiltin { val tpe: FType.FBuiltin = FType.FInt }
  case class FVBool(value: Boolean) extends FVBuiltin { val tpe: FType.FBuiltin = FType.FBool }
  case class FVLong(value: Long) extends FVBuiltin { val tpe: FType.FBuiltin = FType.FLong }
  case class FVDouble(value: Double) extends FVBuiltin { val tpe: FType.FBuiltin = FType.FDouble }

  case class FVField(name: String, value: FValue) {
//    def tpe: FType.FField = FType.FField(name, value.tpe)
  }
  case class FVRecord(fields: List[FVField], tpe: FRecord) extends FValue {
//    override def value: Any = List(fields.map(_.value.value))
  }
  case class FVTuple(tuple: List[FValue], tpe: FTuple) extends FValue {
//    override def value: Any = tuple.map(_.value)
  }

  case class FVFiniteStream(values: List[FValue], tpe: FType) extends FValue {}
}
