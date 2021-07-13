package izumi.flow.dsl.simulator.components

import izumi.flow.dsl.NodeSimulator
import izumi.flow.model.flow.ValueId

trait Registry {
  def node(id: ValueId): NodeSimulator
}
