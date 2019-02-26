package com.github.pshirshov.izumi.idealingua.typer2

import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractIndefiniteId, DomainId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.il.ast.raw.defns.{RawNodeMeta, RawStructure, RawTopLevelDefn, RawTypeDef}
import com.github.pshirshov.izumi.idealingua.typer2.IzType._
import com.github.pshirshov.izumi.idealingua.typer2.IzTypeId.{IzDomainPath, IzName, IzNamespace, IzPackage}
import com.github.pshirshov.izumi.idealingua.typer2.ProcessedOp.Exported
import com.github.pshirshov.izumi.idealingua.typer2.T2Fail._
import com.github.pshirshov.izumi.idealingua.typer2.Typer2.{Identified, UnresolvedName}

import scala.collection.mutable
import scala.reflect.ClassTag

sealed trait TsMember

object TsMember {

  final case class Namespace(prefix: TypePrefix.UserT, types: List[TsMember]) extends TsMember

  final case class UserType(tpe: IzType, sub: List[TsMember]) extends TsMember

}

sealed trait ProcessedOp {
  def member: TsMember
}

object ProcessedOp {

  final case class Exported(member: TsMember) extends ProcessedOp

  final case class Imported(member: TsMember) extends ProcessedOp

}

case class Typespace2(

                       warnings: List[T2Warn],
                       imports: Set[IzTypeId],
                       types: List[TsMember],
                     )

class Ts2Builder(index: DomainIndex, importedIndexes: Map[DomainId, DomainIndex]) {
  private val failed = mutable.HashSet.empty[UnresolvedName]
  private val failures = mutable.ArrayBuffer.empty[InterpretationFail]
  private val existing = mutable.HashSet.empty[UnresolvedName]
  private val types = mutable.HashMap[IzTypeId, ProcessedOp]()
  private val thisPrefix = TypePrefix.UserTLT(IzPackage(index.defn.id.toPackage.map(IzDomainPath)))

  def defined: Set[UnresolvedName] = {
    existing.toSet
  }

  def add(ops: Identified): Unit = {
    ops.defns match {
      case defns if defns.isEmpty =>
      // type requires no ops => builtin

      case single :: Nil =>
        val dindex = if (single.source == index.defn.id) {
          index
        } else {
          importedIndexes(single.source)
        }
        val interpreter = new Interpreter(dindex, types.toMap)

        val product = single.defn match {
          case RawTopLevelDefn.TLDBaseType(v) =>
            v match {
              case i: RawTypeDef.Interface =>
                interpreter.makeInterface(i)

              case d: RawTypeDef.DTO =>
                interpreter.makeDto(d)

              case a: RawTypeDef.Alias =>
                interpreter.makeAlias(a)

              case e: RawTypeDef.Enumeration =>
                interpreter.makeEnum(e)

              case i: RawTypeDef.Identifier =>
                interpreter.makeIdentifier(i)
              case a: RawTypeDef.Adt =>
                interpreter.makeAdt(a)
            }

          case c: RawTopLevelDefn.TLDNewtype =>
            interpreter.cloneType(c.v)

          case RawTopLevelDefn.TLDForeignType(v) =>
            interpreter.makeForeign(v)

          case RawTopLevelDefn.TLDDeclared(v) =>
            Left(List(SingleDeclaredType(v)))
        }
        register(ops, product)

      case o =>
        println(s"Unhandled case: ${o.size} defs")
    }
  }

  def fail(ops: Identified, failures: List[InterpretationFail]): Unit = {
    if (ops.depends.exists(failed.contains)) {
      // dependency has failed already, fine to skip
    } else {
      failed.add(ops.id)
      this.failures ++= failures
    }
  }

  def finish(): Either[List[InterpretationFail], Typespace2] = {
    if (failures.nonEmpty) {
      Left(failures.toList)
    } else {
      Right(
        Typespace2(
          List.empty,
          Set.empty,
          Ts2Builder.this.types.values.collect({ case Exported(member) => member }).toList,
        )
      )
    }
  }

  private def register(ops: Identified, product: Either[List[InterpretationFail], IzType]): Unit = {
    product match {
      case Left(value) =>
        fail(ops, value)

      case Right(value) =>
        val member = TsMember.UserType(value, List.empty)
        types.put(value.id, makeMember(member))
        existing.add(ops.id).discard()
    }
  }

  private def isOwn(id: IzTypeId): Boolean = {
    id match {
      case IzTypeId.BuiltinType(_) =>
        false
      case IzTypeId.UserType(prefix, _) =>
        prefix == thisPrefix
    }
  }

  private def makeMember(member: TsMember.UserType): ProcessedOp = {
    if (isOwn(member.tpe.id)) {
      ProcessedOp.Exported(member)
    } else {
      ProcessedOp.Imported(member)
    }
  }
}

class Interpreter(_index: DomainIndex, types: Map[IzTypeId, ProcessedOp]) {
  private val index: DomainIndex = _index

  def makeForeign(v: RawTypeDef.ForeignType): Either[List[InterpretationFail], IzType] = ???

  def cloneType(v: RawTypeDef.NewType): Either[List[InterpretationFail], IzType] = ???

  def makeAdt(a: RawTypeDef.Adt): Either[List[InterpretationFail], IzType] = ???

  def makeIdentifier(i: RawTypeDef.Identifier): Either[List[InterpretationFail], IzType] = ???

  def makeEnum(e: RawTypeDef.Enumeration): Either[List[InterpretationFail], IzType] = ???


  def makeInterface(i: RawTypeDef.Interface): Either[List[InterpretationFail], IzType.Interface] = {
    val struct = i.struct
    val id = toId(i.id)
    Right(make[IzType.Interface](struct, id, i.meta))
  }

  def makeDto(i: RawTypeDef.DTO): Either[List[InterpretationFail], IzType.DTO] = {
    val struct = i.struct
    val id = toId(i.id)
    Right(make[IzType.DTO](struct, id, i.meta))
  }

  def makeAlias(a: RawTypeDef.Alias): Either[List[InterpretationFail], IzType.IzAlias] = {
    Right(IzAlias(toId(a.id), resolve(a.target), meta(a.meta)))
  }


  private def resolve(id: AbstractIndefiniteId): IzTypeId = {
    val unresolved = index.makeAbstract(id)
    val out = IzTypeId.UserType(TypePrefix.UserTLT(makePkg(unresolved)), IzName(unresolved.name))
    out
  }

  private def makePkg(unresolved: UnresolvedName): IzPackage = {
    IzPackage(unresolved.pkg.map(IzDomainPath))
  }

  private def toId(id: TypeId): IzTypeId = {
    val unresolvedName = index.makeAbstract(id)
    val pkg = makePkg(unresolvedName)
    if (id.path.within.isEmpty) {
      IzTypeId.UserType(TypePrefix.UserTLT(pkg), IzName(id.name))
    } else {
      IzTypeId.UserType(TypePrefix.UserT(pkg, id.path.within.map(IzNamespace)), IzName(id.name))
    }
  }


  private def make[T <: IzStructure : ClassTag](struct: RawStructure, id: IzTypeId, structMeta: RawNodeMeta): T = {
    val parentsIds = struct.interfaces.map(toId)
    val parents = parentsIds.map(types.apply).map(_.member)
    val `+concepts` = struct.concepts.map(resolve).map(types.apply).map(_.member)
    val `-concepts` = struct.removedConcepts.map(resolve).map(types.apply).map(_.member)

    val parentFields = parents.flatMap(structFields)
    val `+conceptFields` = `+concepts`.flatMap(structFields)
    val `-conceptFields` = `-concepts`.flatMap(structFields).map(_.basic)

    val localFields = struct.fields.map {
      f =>
        val typeId = resolve(f.typeId)
        Field2(FName(f.name.getOrElse(typeId.name.name)), IzTypeReference.Scalar(typeId), Seq(id), meta(f.meta))
    }

    val removedFields = struct.removedFields.map {
      f =>
        val typeId = resolve(f.typeId)
        Basic(FName(f.name.getOrElse(typeId.name.name)), IzTypeReference.Scalar(typeId))
    }


    val allRemovals = (`-conceptFields` ++ removedFields).toSet
    val allFields = (parentFields ++ `+conceptFields` ++ localFields).filterNot {
      f => allRemovals.contains(f.basic)
    }


    (if (implicitly[ClassTag[T]].runtimeClass == implicitly[ClassTag[IzType.Interface]].runtimeClass) {
      IzType.Interface(id, allFields, parentsIds, meta(structMeta))
    } else {
      IzType.DTO(id, allFields, parentsIds, meta(structMeta))
    }).asInstanceOf[T]
  }

  private def structFields(tpe: IzType): Seq[Field2] = {
    tpe match {
      case a: IzAlias =>
        structFields(types.apply(a.source).member)
      case structure: IzStructure =>
        structure.fields
      case generic: Generic =>
        ???
      case builtinType: BuiltinType =>
        ???
    }
  }

  private def structFields(p: TsMember): Seq[Field2] = {
    p match {
      case TsMember.UserType(tpe, _) =>
        structFields(tpe)
      case TsMember.Namespace(_, _) =>
        ???
    }
  }

  private def meta(meta: RawNodeMeta): NodeMeta = {
    IzType.NodeMeta(meta.doc, Seq.empty, meta.position)
  }
}
