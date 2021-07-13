package izumi.flow.dsl.simulator.model

import izumi.flow.dsl.simulator.processing.NodeState

case class PollingState(node: NodeState, finished: Boolean)
