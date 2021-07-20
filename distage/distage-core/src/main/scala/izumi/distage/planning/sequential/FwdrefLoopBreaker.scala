package izumi.distage.planning.sequential

import izumi.distage.model.definition.errors.DIError.LoopResolutionError
import izumi.distage.model.definition.errors.DIError.LoopResolutionError.{BUG_BestLoopResolutionIsNotSupported, BestLoopResolutionCannotBeProxied}
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.WiringOp.ReferenceKey
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, SemiplanOp}
import izumi.distage.model.planning.PlanAnalyzer
import izumi.distage.model.reflection.{DIKey, MirrorProvider}
import izumi.distage.planning.sequential.FwdrefLoopBreaker.BreakAt
import izumi.fundamentals.graphs.DG

trait FwdrefLoopBreaker {
  def breakLoop(withLoops: Map[DIKey, Set[DIKey]], plan: DG[DIKey, ExecutableOp.SemiplanOp]): Either[List[LoopResolutionError], BreakAt]
}

object FwdrefLoopBreaker {
  case class BreakAt(dependee: DIKey, dependencies: Set[DIKey])

  class FwdrefLoopBreakerDefaultImpl(
    mirrorProvider: MirrorProvider,
    analyzer: PlanAnalyzer,
  ) extends FwdrefLoopBreaker {
    override def breakLoop(withLoops: Map[DIKey, Set[DIKey]], plan: DG[DIKey, ExecutableOp.SemiplanOp]): Either[List[LoopResolutionError], BreakAt] = {
      val best = withLoops.keys.toVector.sortWith {
        case (fst, snd) =>
          val fsto = plan.meta.nodes(fst)
          val sndo = plan.meta.nodes(snd)
          val fstp = mirrorProvider.canBeProxied(fsto.target.tpe) && !effectKey(fsto.target)
          val sndp = mirrorProvider.canBeProxied(sndo.target.tpe) && !effectKey(sndo.target)

          if (fstp && !sndp) {
            true
          } else if (!fstp) {
            false
          } else if (!referenceOp(fsto) && referenceOp(sndo)) {
            true
          } else if (referenceOp(fsto)) {
            false
          } else {
            val fstHasByName: Boolean = hasByNameParameter(fsto)
            val sndHasByName: Boolean = hasByNameParameter(sndo)

            // reverse logic? prefer by-names ???
            //            if (!fstHasByName && sndHasByName) {
            //              true
            //            } else if (fstHasByName && !sndHasByName) {
            //              false
            //            } else {
            //              analyzer.requirements(fsto).size > analyzer.requirements(sndo).size
            //            }
            if (fstHasByName && !sndHasByName) {
              true
            } else if (!fstHasByName && sndHasByName) {
              false
            } else {
              analyzer.requirements(fsto).size > analyzer.requirements(sndo).size
            }
          }
      }.head

      plan.meta.nodes(best) match {
        case op: ReferenceKey =>
          Left(List(BUG_BestLoopResolutionIsNotSupported(op)))
        case op: ImportDependency =>
          Left(List(BUG_BestLoopResolutionIsNotSupported(op)))
        case op: InstantiationOp if !mirrorProvider.canBeProxied(op.target.tpe) && hasNonByNameUses(plan, op.target) =>
          Left(List(BestLoopResolutionCannotBeProxied(op)))
        case _: InstantiationOp =>
          assert(withLoops.contains(best))
          Right(BreakAt(best, withLoops(best)))
      }
    }


    private[this] def effectKey(key: DIKey): Boolean = key match {
      case _: DIKey.ResourceKey | _: DIKey.EffectKey => true
      case _ => false
    }

    private[this] def referenceOp(s: SemiplanOp): Boolean = s match {
      case _: ReferenceKey /*| _: MonadicOp */ => true
      case _ => false
    }

    private[this] def hasByNameParameter(fsto: ExecutableOp): Boolean = {
      fsto match {
        case op: ExecutableOp.WiringOp =>
          op.wiring.associations.exists(_.isByName)
        case _ =>
          false
      }
    }

    private[this] def hasNonByNameUses(plan: DG[DIKey, ExecutableOp.SemiplanOp], key: DIKey): Boolean = {
      val directDependees = plan.successors.links(key)
      plan.meta.nodes.values.filter(directDependees contains _.target).exists {
        case op: ExecutableOp.WiringOp =>
          op.wiring.associations.exists(param => param.key == key && !param.isByName)
        case _ => false
      }
    }

  }
}
