package izumi.flow.dsl.simulator.model

import izumi.flow.model.values.FValue

sealed trait StreamState

object StreamState {
  sealed trait Final extends StreamState
  case class ChunkReady(chunk: List[FValue]) extends StreamState
  case object ChunkNotReady extends StreamState
  case object StreamFinished extends Final
  case class StreamErrored() extends Final
}
