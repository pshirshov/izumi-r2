package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.definition.Binding
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.{ImportDependency, SetOp}
import org.bitbucket.pshirshov.izumi.di.model.plan._

class PlanMergingPolicyDefaultImpl extends PlanMergingPolicy {
  def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan = {
    val oldImports = currentPlan.imports.keySet

    val newProvisionKeys = currentOp
      .provisions
      .map(op => op.target)
      .toSet

    val safeNewProvisions = if (oldImports.intersect(newProvisionKeys).isEmpty) {
      currentPlan.steps ++ currentOp.provisions
    } else {
      currentOp.provisions ++ currentPlan.steps
    }

    val newImports = computeNewImports(currentPlan, currentOp)
    val newSets = currentPlan.sets ++ currentOp.sets


    val newPlan = DodgyPlan(
      newImports
      , newSets
      , safeNewProvisions
      , currentPlan.issues
    )

    val issues: Seq[PlanningFailure] = newPlan
      .statements
      .groupBy(_.target)
      .filter(_._2.lengthCompare(1) > 0)
      .filterNot(_._2.forall(_.isInstanceOf[SetOp]))
      .map {
        case (key, values) if values.toSet.size == 1 =>
          PlanningFailure.DuplicatedStatements(key, values)
        case (key, values) =>
          PlanningFailure.UnsolvableConflict(key, values)
      }
      .toSeq

    newPlan.copy(issues = newPlan.issues ++ issues)
  }

  private def computeNewImports(currentPlan: DodgyPlan, currentOp: NextOps) = {
    val newProvisionKeys = currentOp
      .provisions
      .map(op => op.target)
      .toSet

    val currentImportsMap = currentPlan.imports
      .values
      .filterNot(imp => newProvisionKeys.contains(imp.target))
      .map(imp => (imp.target, imp))

    val newImportsMap = currentOp.imports
      .filterNot(imp => newProvisionKeys.contains(imp.target))
      .map(imp => (imp.target, imp))

    val newImports = newImportsMap.foldLeft(currentImportsMap.toMap) {
      case (acc, (target, op)) =>
        val importOp = acc.getOrElse(target, op)
        acc.updated(target, ImportDependency(target, importOp.references ++ op.references))
    }
    newImports
  }
}
