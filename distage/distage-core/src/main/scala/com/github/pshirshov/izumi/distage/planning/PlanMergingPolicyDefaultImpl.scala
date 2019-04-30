package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.exceptions.ConflictingDIKeyBindingsException
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy
import com.github.pshirshov.izumi.distage.model.planning.PlanMergingPolicy.{DIKeyConflictResolution, WithResolve}
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import distage.DIKey

import scala.collection.mutable


class PlanMergingPolicyDefaultImpl() extends PlanMergingPolicy with WithResolve {

  override final def freeze(plan: DodgyPlan): SemiPlan = {
    val resolved = mutable.HashMap[DIKey, Set[ExecutableOp]]()
    val issues = mutable.HashMap[DIKey, DIKeyConflictResolution.Failed]()

    plan.freeze.foreach {
      case (k, v) =>
        resolve(plan, k, v) match {
          case DIKeyConflictResolution.Successful(op) =>
            resolved.put(k, op)
          case f: DIKeyConflictResolution.Failed =>
            issues.put(k, f)
        }
    }

    if (issues.nonEmpty) {
      handleIssues(plan, resolved.toMap, issues.toMap)
    } else {
      SemiPlan(plan.definition, resolved.values.flatten.toVector, plan.roots)
    }
  }


  protected def handleIssues(plan: DodgyPlan, resolved: Map[DIKey, Set[ExecutableOp]], issues: Map[DIKey, DIKeyConflictResolution.Failed]): SemiPlan = {
    Quirks.discard(plan, resolved)
    printIssues(issues)
  }

  protected def printIssues(issues: Map[distage.DIKey, DIKeyConflictResolution.Failed]): Nothing = {
    import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
    // TODO: issues == slots, we may apply slot logic here
    val issueRepr = issues.map {
      case (k, f) =>
        s"""Conflict resolution failed key $k with reason
           |
           |${f.explanation.shift(4)}
           |
           |    Candidates left: ${f.candidates.niceList().shift(4)}""".stripMargin
    }

    throw new ConflictingDIKeyBindingsException(
      s"""There must be exactly one valid binding for each DIKey.
         |
         |You can use named instances: `make[X].named("id")` method and `distage.Id` annotation to disambiguate
         |between multiple instances of the same type.
         |
         |List of problematic bindings: ${issueRepr.niceList()}
         """.stripMargin
      , issues
    )
  }
}
