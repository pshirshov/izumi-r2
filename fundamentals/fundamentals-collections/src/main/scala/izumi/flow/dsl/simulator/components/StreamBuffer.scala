package izumi.flow.dsl.simulator.components

import izumi.flow.dsl.simulator.model.StreamState

trait StreamBuffer {
  def nextStates(outChunk: List[StreamState]): Unit
}
