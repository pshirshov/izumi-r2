package izumi.distage.model

import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.definition.{Activation, ModuleBase}
import izumi.distage.model.plan._

/** Transforms [[izumi.distage.model.definition.ModuleDef]] into [[izumi.distage.model.plan.DIPlan]] */
trait Planner {
  def plan(input: PlannerInput): DIPlan
  def planNoRewrite(input: PlannerInput): DIPlan

  def planSafe(input: PlannerInput): Either[List[DIError], DIPlan]
  def planNoRewriteSafe(input: PlannerInput): Either[List[DIError], DIPlan]

  def rewrite(bindings: ModuleBase): ModuleBase

  @inline final def planSafe(bindings: ModuleBase, activation: Activation, roots: Roots): Either[List[DIError], DIPlan] =
    planSafe(PlannerInput(bindings, activation, roots))

  @inline final def planSafe(bindings: ModuleBase, roots: Roots): Either[List[DIError], DIPlan] =
    planSafe(bindings, Activation.empty, roots)

  @inline final def plan(bindings: ModuleBase, activation: Activation, roots: Roots): DIPlan =
    plan(PlannerInput(bindings, activation, roots))

  @inline final def plan(bindings: ModuleBase, roots: Roots): DIPlan =
    plan(bindings, Activation.empty, roots)
}
