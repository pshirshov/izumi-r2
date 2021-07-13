package izumi.flow.dsl

import izumi.flow.dsl.simulator.components.Registry
import izumi.flow.model.flow.{Flow, ValueId}

class Simulation(flow: Flow) extends Registry {
  private val nodes = flow.ops.map(op => (op.output, new NodeSimulator(op, flow.dependees.links(op.output), this, flow.outputs.contains(op.output)))).toMap

  override def node(id: ValueId): NodeSimulator = synchronized {
    nodes(id)
  }

  def run(): Unit = {
    val threads = nodes.values.map(_.makeThread())
    threads.foreach(_.start())
    threads.foreach(_.join())
    //println(streams)
    println("Simulation finished")

  }
}
