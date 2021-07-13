package izumi.flow.dsl.simulator.processing

import izumi.flow.dsl.simulator.model.PollingState
import izumi.flow.model.values.FValue

trait Processor {
  def process(state: NodeState): PollingState
}

sealed trait NodeState {}

object NodeState {
  case object Initial extends NodeState
  case class Value(value: FValue) extends NodeState

}
