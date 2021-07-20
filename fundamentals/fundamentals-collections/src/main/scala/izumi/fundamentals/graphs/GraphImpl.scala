package izumi.fundamentals.graphs

import izumi.fundamentals.graphs.struct.IncidenceMatrix

sealed trait GraphImpl[N, +M] { this: AbstractGraph[N, M] => }

object GraphImpl {

  trait DirectedGraphSucc[N, +M] extends GraphImpl[N, M] { this: AbstractGraph[N, M] =>
    def successors: IncidenceMatrix[N]
    def noSuccessors: Set[N] = successors.links.filter(_._2.isEmpty).keys.toSet
  }

  trait DirectedGraphPred[N, +M] extends GraphImpl[N, M] { this: AbstractGraph[N, M] =>
    def predecessors: IncidenceMatrix[N]
    def noPredcessors: Set[N] = predecessors.links.filter(_._2.isEmpty).keys.toSet
  }

}
