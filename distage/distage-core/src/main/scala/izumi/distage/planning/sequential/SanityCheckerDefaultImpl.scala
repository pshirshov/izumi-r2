package izumi.distage.planning.sequential

import distage.Roots
import izumi.distage.model.definition.errors.DIError
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.ProxyOp
import izumi.distage.model.planning.{PlanAnalyzer, SanityChecker}
import izumi.distage.model.reflection.DIKey
import izumi.functional.IzEither._
import izumi.fundamentals.graphs.DG

class SanityCheckerDefaultImpl(
  protected val planAnalyzer: PlanAnalyzer
) extends SanityChecker {

  override def verifyPlan(plan: DG[DIKey, ExecutableOp], roots: Roots): Either[List[DIError.VerificationError], Unit] = {
    val inconsistentKeys = plan.meta.nodes.filter { case (k, op) => k != op.target }
    val unreferencedKeys = plan.meta.nodes.keySet -- plan.successors.links.keySet
    val missingKeys = plan.successors.links.keySet -- plan.meta.nodes.keySet
    val matricesRepresentSameGraph = plan.successors == plan.predecessors.transposed

    val missingRefs = {
      val allAvailableRefs = plan.predecessors.links.keySet
      val fullDependenciesSet = plan.predecessors.links.flatMap(_._2).toSet
      fullDependenciesSet -- allAvailableRefs
    }

    val (missingProxies, missingInits) = {
      val ops = plan.meta.nodes.values.toList
      val proxyInits = ops.collect { case op: ProxyOp.InitProxy => op }
      val proxies = ops.collect { case op: ProxyOp.MakeProxy => op }
      val proxyInitSources = proxyInits.map(_.target.proxied: DIKey)
      val proxyKeys = proxies.map(_.target: DIKey)
      // every proxy op has matching init op
      val missingProxies = proxyKeys.diff(proxyInitSources).toSet
      // every init op has matching proxy op
      val missingInits = proxyInitSources.diff(proxyKeys).toSet
      (missingProxies, missingInits)
    }

    val missingRoots = roots match {
      case Roots.Of(roots) =>
        roots.toSet.diff(plan.meta.nodes.keySet)
      case Roots.Everything =>
        Set.empty[DIKey]
    }

    def failIf(cond: => Boolean)(error: => DIError.VerificationError): Either[List[DIError.VerificationError], Unit] = {
      if (cond) {
        Left(List(error))
      } else {
        Right(())
      }
    }

    val checks = Seq(
      failIf(!matricesRepresentSameGraph)(DIError.VerificationError.BUG_PlanMatricesInconsistent(plan)),
      failIf(inconsistentKeys.nonEmpty)(DIError.VerificationError.BUG_PlanIndexIsBroken(inconsistentKeys)),
      failIf(unreferencedKeys.nonEmpty)(DIError.VerificationError.BUG_PlanIndexHasUnrequiredOps(unreferencedKeys)),
      failIf(missingKeys.nonEmpty)(DIError.VerificationError.PlanReferencesMissingOperations(missingKeys)),
      failIf(missingRefs.nonEmpty)(DIError.VerificationError.MissingRefException(missingRefs, plan)),
      failIf(missingProxies.nonEmpty)(DIError.VerificationError.BUG_InitWithoutProxy(missingProxies)),
      failIf(missingInits.nonEmpty)(DIError.VerificationError.BUG_ProxyWithoutInit(missingInits)),
      failIf(missingRoots.nonEmpty)(DIError.VerificationError.BUG_ProxyWithoutInit(missingRoots)),
    )

    checks.biAggregateVoid
  }
}
