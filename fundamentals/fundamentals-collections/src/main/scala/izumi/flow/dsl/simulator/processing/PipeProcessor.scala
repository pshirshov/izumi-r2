package izumi.flow.dsl.simulator.processing

import izumi.flow.dsl.simulator.components.{NsInterpreter, Registry, StreamBuffer}
import izumi.flow.dsl.simulator.model.{PollingState, StreamState}
import izumi.flow.model.flow.FlowOp.{FFilter, FFold, FMap, PipeOp}
import izumi.flow.model.schema.FType
import izumi.flow.model.values.FValue

class PipeProcessor(op: PipeOp, registry: Registry, buffer: StreamBuffer, nsi: NsInterpreter) extends Processor {
  def process(state: NodeState): PollingState = {
    registry.node(op.input).poll(op.output) match {
      case _: StreamState.Final =>
        PollingState(state, finished = true)
      case StreamState.ChunkReady(chunk) =>
        val next = interpretPipe(state, op, chunk)
        buffer.nextStates(next._2)
        PollingState(next._1, finished = false)
      case StreamState.ChunkNotReady =>
        PollingState(state, finished = false)
    }
  }

  private def interpretPipe(state: NodeState, op: PipeOp, chunk: List[FValue]): (NodeState, List[StreamState]) = {
    val it = op.input.tpe.asInstanceOf[FType.FStream].tpe
    op match {
      case m: FMap =>
        val outt = op.output.tpe.asInstanceOf[FType.FStream].tpe
        val mapped = chunk.map {
          i =>
            assert(i.tpe == i.tpe)
            nsi.interpretNs(i, outt, m.expr)
        }
        (state, List(StreamState.ChunkReady(mapped)))
      case m: FFilter =>
        val outt = op.output.tpe.asInstanceOf[FType.FStream].tpe
        assert(it == outt)

        val mapped = chunk.filter {
          i =>
            assert(i.tpe == it)
            nsi.interpretNs(i, FType.FBool, m.expr) match {
              case FValue.FVBool(value) =>
                value
              case _ =>
                ???
            }
        }
        val next = if (mapped.nonEmpty) {
          List(StreamState.ChunkReady(mapped))
        } else {
          List(StreamState.ChunkNotReady)
        }
        (state, next)
      case op: FFold =>
        val accState = state match {
          case NodeState.Initial =>
            op.initial
          case NodeState.Value(value) =>
            value
        }
        assert(!op.output.tpe.isInstanceOf[FType.FStream])

        val f = chunk.foldRight(accState) {
          case (i, acc) =>
            assert(i.tpe == it)
            assert(it.isInstanceOf[FType.FTuple])
            nsi.interpretNs(
              FValue.FVTuple(
                List(acc, i),
                it.asInstanceOf[FType.FTuple],
              ),
              op.output.tpe,
              op.expr,
            )
        }
        (NodeState.Value(f), List(StreamState.ChunkNotReady))
    }
  }
}
