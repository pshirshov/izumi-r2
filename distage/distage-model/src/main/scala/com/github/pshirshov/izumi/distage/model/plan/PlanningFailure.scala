package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.definition.{Binding, ImplDef}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse


sealed trait PlanningFailure {}

object PlanningFailure {
  case class UnsolvableConflict(target: RuntimeDIUniverse.DIKey, ops: Seq[ExecutableOp]) extends PlanningFailure
  case class UnbindableBinding(binding: Binding, defs: Seq[ImplDef]) extends PlanningFailure
}
