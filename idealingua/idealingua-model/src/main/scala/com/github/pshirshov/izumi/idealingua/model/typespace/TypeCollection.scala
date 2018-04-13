package com.github.pshirshov.izumi.idealingua.model.typespace

import com.github.pshirshov.izumi.idealingua.model.common.{IndefiniteId, TypeId}
import com.github.pshirshov.izumi.idealingua.model.common.TypeId.{AdtId, DTOId, InterfaceId, ServiceId}
import com.github.pshirshov.izumi.idealingua.model.exceptions.IDLException
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.Service.DefMethod.{DeprecatedRPCMethod, Output, RPCMethod}
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed.TypeDef._
import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._

class TypeCollection(domain: DomainDefinition) {
  val services: Map[ServiceId, Service] = domain.services.groupBy(_.id).mapValues(_.head)

  val serviceEphemerals: Seq[TypeDef] = (for {
    service <- services.values
    method <- service.methods
  } yield {
    method match {
      case m: DeprecatedRPCMethod =>
        Seq.empty
//        val inIid = DTOId(service.id, s"In${m.name.capitalize}")
//        val outIid = DTOId(service.id, s"Out${m.name.capitalize}")
//
//        Seq(
//          DTO(inIid, Structure.interfaces(m.signature.input))
//          , DTO(outIid, Structure.interfaces(m.signature.output))
//        )
      case m: RPCMethod =>
        val baseName = m.name.capitalize

        val inputDto = {
          val in = m.signature.input
          val inputStructure = Structure.apply(in.fields, List.empty, Super(List.empty, in.concepts, List.empty))
          val inId = DTOId(IndefiniteId(service.id), s"${baseName}Input")
          DTO(inId, inputStructure)
        }


        val outDto = m.signature.output match {
          case o: Output.Singular =>
            val outStructure = Structure.apply(List(Field(o.typeId, "value")), List.empty, Super.empty)
            val outId = DTOId(IndefiniteId(service.id), s"${baseName}Output")
            DTO(outId, outStructure)

          case o: Output.Struct =>
            val outStructure = Structure.apply(o.struct.fields, List.empty, Super(List.empty, o.struct.concepts, List.empty))
            val outId = DTOId(IndefiniteId(service.id), s"${baseName}Output")
            DTO(outId, outStructure)

          case o: Output.Algebraic =>
            val outId = AdtId(IndefiniteId(service.id), s"${baseName}Output")
            Adt(outId, o.alternatives)
        }

        Seq(inputDto, outDto)
    }
  }).flatten.toSeq

  val interfaceEphemerals: Seq[DTO] = {
    domain.types
      .collect {
        case i: Interface =>
          val iid = DTOId(i.id, toDtoName(i.id))
          DTO(iid, Structure.interfaces(List(i.id)))
      }
  }

  val all: Seq[TypeDef] = {
    val definitions = Seq(
      domain.types
      , serviceEphemerals
      , interfaceEphemerals
    ).flatten

    verified(definitions)
  }

  val structures: Seq[WithStructure] = all.collect { case t: WithStructure => t }

  def index: Map[TypeId, TypeDef] = {
    all.map(t => (t.id, t)).toMap
  }

  def toDtoName(id: TypeId): String = {
    id match {
      case _: InterfaceId =>
        s"${id.name}Struct"
      case _ =>
        s"${id.name}"

    }
  }

  protected def verified(types: Seq[TypeDef]): Seq[TypeDef] = {
    val conflictingTypes = types.groupBy(_.id).filter(_._2.lengthCompare(1) > 0)
    if (conflictingTypes.nonEmpty) {
      throw new IDLException(s"Conflicting types in: $conflictingTypes")
    }

    types
  }
}
