package izumi.flow.dsl.simulator.processing

import izumi.flow.dsl.simulator.components.StreamBuffer
import izumi.flow.dsl.simulator.model.{PollingState, StreamState}
import izumi.flow.model.flow.FlowOp.{FConstMulti, SourceOp}

class SourceProcessor(op: SourceOp, buffer: StreamBuffer) extends Processor {
  def process(state: NodeState): PollingState = {
    op match {
      case FConstMulti(output, value) =>
        val onePerChunk = value.map(v => StreamState.ChunkReady(List(v)))
        buffer.nextStates(onePerChunk ++ List(StreamState.StreamFinished))
        PollingState(state, finished = true)
    }
  }
}
