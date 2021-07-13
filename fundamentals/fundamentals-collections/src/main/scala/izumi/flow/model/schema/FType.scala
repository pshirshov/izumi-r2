package izumi.flow.model.schema

sealed trait FType {}

object FType {
  sealed trait FBuiltin extends FType
  case object FString extends FBuiltin
  case object FInt extends FBuiltin
  case object FBool extends FBuiltin
  case object FLong extends FBuiltin
  case object FDouble extends FBuiltin

  case class FField(name: String, tpe: FType)
  case class FRecord(fields: List[FField]) extends FType

  case class FTuple(tuple: List[FType]) extends FType

  case class FStream(tpe: FType) extends FType
}
