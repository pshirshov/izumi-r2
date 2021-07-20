package izumi.distage.model.plan

import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.platform.strings.IzString._

/**
  * @param primary plan producing roots, must be inherited from the `side` plan
  * @param side    integrations, must be inhertied from the `shared` plan
  * @param shared  creates a part of the graph shared by both `side` and `primary` plans
  */
final case class TriSplittedPlan(
  side: DIPlan,
  primary: DIPlan,
  shared: DIPlan,
  sideRoots1: Set[DIKey],
  sideRoots2: Set[DIKey],
) {
  def keys: Set[DIKey] = side.keys ++ primary.keys ++ shared.keys
  def isEmpty: Boolean = {
    side.steps.isEmpty &&
    primary.steps.isEmpty &&
    shared.steps.isEmpty &&
    sideRoots1.isEmpty &&
    sideRoots2.isEmpty
  }
  def nonEmpty: Boolean = !isEmpty
}

object TriSplittedPlan {
  implicit final class TriPlanEx(private val split: TriSplittedPlan) extends AnyVal {
    def render(): String = {
      Seq(
        split.shared.render().listing("Shared Plan"),
        split.side.render().listing("Side Plan"),
        split.primary.render().listing("Primary Plan"),
      ).mkString("\n")
    }
  }

}
