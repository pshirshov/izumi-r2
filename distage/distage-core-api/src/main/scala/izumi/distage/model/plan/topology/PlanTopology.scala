package izumi.distage.model.plan.topology

import izumi.distage.model.reflection._

/**
  * This class represents direct node dependencies and allows to retrieve full transitive dependencies for a node
  */
sealed trait PlanTopology {
  def dependees: DependencyGraph

  def dependencies: DependencyGraph

  /**
    * This method is relatively expensive
    *
    * @return full set of all the keys transitively depending on key
    */
  def transitiveDependees(key: DIKey): Set[DIKey] = dependees.transitive(key)

  /**
    * This method is relatively expensive
    *
    * @return full set of all the keys transitively depending on key
    */
  def transitiveDependencies(key: DIKey): Set[DIKey] = dependencies.transitive(key)
}

object PlanTopology {
  final case class PlanTopologyImmutable(
    dependees: DependencyGraph,
    dependencies: DependencyGraph,
  ) extends PlanTopology
}
