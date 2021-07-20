package izumi.distage.model.definition.errors

import izumi.distage.model.definition.conflicts.ConflictResolutionError
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.InstantiationOp
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.DG

sealed trait DIError

object DIError {

  sealed trait LoopResolutionError extends DIError

  object LoopResolutionError {
    final case class BUG_UnableToFindLoop(predcessors: Map[DIKey, Set[DIKey]]) extends LoopResolutionError
    final case class BUG_BestLoopResolutionIsNotSupported(op: ExecutableOp.SemiplanOp) extends LoopResolutionError
    final case class BestLoopResolutionCannotBeProxied(op: InstantiationOp) extends LoopResolutionError
  }

  final case class ConflictResolutionFailed(error: ConflictResolutionError[DIKey, InstantiationOp]) extends DIError

  sealed trait VerificationError extends DIError

  object VerificationError {
    final case class BUG_PlanIndexIsBroken(badIndex: Map[DIKey, ExecutableOp]) extends VerificationError
    final case class BUG_PlanIndexHasUnrequiredOps(unreferencedInGraph: Set[DIKey]) extends VerificationError
    final case class BUG_PlanMatricesInconsistent(plan: DG[DIKey, ExecutableOp]) extends VerificationError
    final case class BUG_InitWithoutProxy(missingProxies: Set[DIKey]) extends VerificationError
    final case class BUG_ProxyWithoutInit(missingInits: Set[DIKey]) extends VerificationError

    final case class PlanReferencesMissingOperations(missingInOpsIndex: Set[DIKey]) extends VerificationError
    final case class MissingRefException(missing: Set[DIKey], plan: DG[DIKey, ExecutableOp]) extends VerificationError
    final case class MissingRoots(missingRoots: Set[DIKey]) extends VerificationError
  }

}
