package izumi.flow.dsl.simulator.processing

import izumi.flow.dsl.simulator.components.{Registry, StreamBuffer}
import izumi.flow.dsl.simulator.model.{PollingState, StreamState}
import izumi.flow.model.flow.FlowOp.MultiOp
import izumi.flow.model.flow.{FlowOp, ValueId}
import izumi.flow.model.schema.FType
import izumi.flow.model.values.FValue

import scala.collection.mutable

class MultiProcessor(op: MultiOp, registry: Registry, buffer: StreamBuffer) extends Processor {
  private val acc = mutable.HashMap.empty[ValueId, mutable.ArrayBuffer[FValue]]

  {
    op.inputs.foreach(i => acc.getOrElseUpdate(i, mutable.ArrayBuffer.empty[FValue]))
  }

  override def process(state: NodeState): PollingState = {
    //val it = op.input.tpe.asInstanceOf[FType.FStream].tpe
    val outt = op.output.tpe.asInstanceOf[FType.FStream].tpe
    op match {
      case FlowOp.FZip(inputs, output) =>
        assert(outt.isInstanceOf[FType.FTuple])
        val outTuple = outt match {
          case t: FType.FTuple =>
            t
          case _ =>
            ???
        }
        val chunks = op.inputs.filterNot(i => acc(i).nonEmpty).map(i => (i, registry.node(i).poll(op.output)))
        var inputsFinished = false

        chunks.foreach {
          case (iid, chunk) =>
            chunk match {
              case StreamState.ChunkReady(chunk) =>
                acc(iid) ++= chunk
              case value: StreamState.Final =>
                inputsFinished = true
                value match {
                  case StreamState.StreamFinished =>
                  // we need to process accumulated data and exit
                  case StreamState.StreamErrored() =>
                    return PollingState(state, finished = false)
                }
              case StreamState.ChunkNotReady =>
              // we need to wait until we accumulate enough inputs
            }
        }

        val next = mutable.ArrayBuffer.empty[FValue]
        while (acc.forall(_._2.nonEmpty)) {
          val nextTuple = inputs.map(i => acc(i).remove(0))
          assert(nextTuple.map(_.tpe) == outTuple.tuple, s"${nextTuple.map(_.tpe)} == ${outTuple.tuple}")
          next += FValue.FVTuple(nextTuple, outTuple)
        }

        val ns = if (next.nonEmpty) {
          List(StreamState.ChunkReady(next.toList))
        } else {
          List.empty
        }

        val (n, r) = if (inputsFinished && acc.exists(_._2.nonEmpty)) {
          (List(StreamState.StreamErrored()), PollingState(state, finished = true))
        } else if (inputsFinished) {
          (List(StreamState.StreamFinished), PollingState(state, finished = true))
        } else {

          (
            if (ns.nonEmpty) { List.empty }
            else { List(StreamState.ChunkNotReady) },
            PollingState(state, finished = false),
          )
        }

        val out = ns ++ n
        if (out.nonEmpty) {
          buffer.nextStates(out)
        }
        r
    }
  }

}
