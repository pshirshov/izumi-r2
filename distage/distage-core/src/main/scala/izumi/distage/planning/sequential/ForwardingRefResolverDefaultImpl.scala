package izumi.distage.planning.sequential

import izumi.distage.model.definition.errors.DIError.LoopResolutionError
import izumi.distage.model.definition.errors.DIError.LoopResolutionError.BUG_UnableToFindLoop
import izumi.distage.model.exceptions.SanityCheckFailedException
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, InstantiationOp, ProxyOp}
import izumi.distage.model.planning.ForwardingRefResolver
import izumi.distage.model.reflection._
import izumi.distage.planning.sequential.FwdrefLoopBreaker.BreakAt
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.cycles.LoopDetector
import izumi.fundamentals.graphs.{DG, GraphMeta}

import scala.annotation.{nowarn, tailrec}
import scala.collection.mutable

@nowarn("msg=Unused import")
class ForwardingRefResolverDefaultImpl(
                                        breaker: FwdrefLoopBreaker
                                      ) extends ForwardingRefResolver {

  import scala.collection.compat._


  override def resolveMatrix(plan: DG[DIKey, ExecutableOp.SemiplanOp]): Either[List[LoopResolutionError], DG[DIKey, ExecutableOp]] = {
    val updatedPlan = mutable.HashMap.empty[DIKey, ExecutableOp]
    val updatedPredcessors = mutable.HashMap.empty[DIKey, mutable.HashSet[DIKey]]
    val replacements = mutable.HashMap.empty[DIKey, mutable.HashSet[(DIKey, DIKey)]]

    def register(dependee: DIKey, deps: Set[DIKey]): Unit = {
      updatedPredcessors.getOrElseUpdate(dependee, mutable.HashSet.empty) ++= deps
      ()
    }

    @tailrec
    def next(predcessors: Map[DIKey, Set[DIKey]]): Either[List[LoopResolutionError], Unit] = {
      val resolved = predcessors.filter(kv => kv._2.forall(updatedPredcessors.contains))
      if (resolved.nonEmpty) {
        resolved.foreach {
          case (r, d) =>
            register(r, d)
            assert(plan.meta.nodes.contains(r))
            updatedPlan.put(r, plan.meta.nodes(r))
        }

        val reduced = predcessors -- resolved.keySet
        next(reduced)
      } else if (predcessors.nonEmpty) {
        val loopMembers: Map[DIKey, Set[DIKey]] = predcessors.view.filterKeys(isInvolvedIntoCycle(predcessors)).toMap

        def processLoopResolution(resolution: BreakAt): Unit = {
          /*
          Our goal is to introduce two operations, MakeProxy and InitProxy.
          All the usages outside of the referential loop will reference InitProxy by its synthetic key, thus we will maintain proper ordering
          MakeProxy will use original key and only ops involved into the loop will dereference it
           */
          val dependee = resolution.dependee
          val dependencies = resolution.dependencies
          val originalOp = plan.meta.nodes(dependee)
          assert(originalOp != null)
          assert(originalOp.target == dependee)

          val onlyByNameUsages = allUsagesAreByName(plan.meta.nodes, dependee, plan.successors.links(dependee))
          val byNameAllowed = onlyByNameUsages

          val badDeps = dependencies.intersect(predcessors.keySet)
          val op = ProxyOp.MakeProxy(originalOp.asInstanceOf[InstantiationOp], badDeps, originalOp.origin, byNameAllowed)
          val initOpKey = DIKey.ProxyInitKey(op.target)

          val loops = LoopDetector.Impl.findCyclesForNode(dependee, plan.predecessors)

          val loopUsers = loops.toList.flatMap(_.loops.flatMap(_.loop)).toSet
          val allUsers = plan.successors.links(dependee)
          val toRewrite = allUsers -- loopUsers - dependee

          toRewrite.foreach {
            k =>
              replacements.getOrElseUpdate(k, mutable.HashSet.empty) += ((dependee, initOpKey: DIKey))
          }


          val goodDeps = dependencies -- badDeps

          register(dependee, goodDeps)
          register(initOpKey, badDeps ++ Set(dependee))

          updatedPlan.put(dependee, op)
          updatedPlan.put(initOpKey, ProxyOp.InitProxy(initOpKey, badDeps, op, op.origin))
          ()
        }

        if (loopMembers.isEmpty) {
          Left(List(BUG_UnableToFindLoop(predcessors)))
        } else {
          breaker.breakLoop(loopMembers, plan) match {
            case Left(value) =>
              Left(value)
            case Right(resolution) =>
              processLoopResolution(resolution)
              next(predcessors -- Set(resolution.dependee))
          }
        }
      } else {
        Right(())
      }
    }

    for {
      _ <- next(plan.predecessors.links)
    } yield {
      replacements.foreach {
        case (in, repls) =>
          val orig = updatedPlan(in)
          assert(orig != null)

          val maybeIdx = repls.groupBy(_._1) // TODO: check uniq
          assert(maybeIdx.forall(_._2.size == 1))
          val idx = repls.toMap

          val upd = orig match {
            case op: InstantiationOp =>
              op.replaceKeys(identity, k => idx.getOrElse(k, k))
            case mp: ProxyOp.MakeProxy =>
              mp.copy(op = mp.op.replaceKeys(identity, k => idx.getOrElse(k, k)))
            case o =>
              throw new SanityCheckFailedException(s"BUG: $o is not an operation which expected to be a user of a cycle")
          }
          val preds = updatedPredcessors(in)
          updatedPredcessors.put(in, preds.map(k => idx.getOrElse(k, k)))
          updatedPlan.put(in, upd)
      }

      val p = IncidenceMatrix(updatedPredcessors.view.mapValues(_.toSet).toMap)
      DG.fromPred(p, GraphMeta(updatedPlan.toMap))
    }
  }

  private[this] def isInvolvedIntoCycle[T](toPreds: Map[T, Set[T]])(key: T): Boolean = {
    test(toPreds, Set.empty, key, key)
  }

  private[this] def test[T](toPreds: Map[T, Set[T]], stack: Set[T], toTest: T, needle: T): Boolean = {
    val deps = toPreds.getOrElse(toTest, Set.empty)

    if (deps.contains(needle)) {
      true
    } else {
      deps.exists {
        d =>
          if (stack.contains(d)) {
            false
          } else {
            test(toPreds, stack + d, d, needle)
          }
      }
    }
  }

  private[this] def allUsagesAreByName(index: Map[DIKey, ExecutableOp.SemiplanOp], target: DIKey, usages: Set[DIKey]): Boolean = {
    val usedByOps = (usages + target).map(index.apply)
    val associations = usedByOps.flatMap {
      case op: InstantiationOp =>
        op match {
          case ExecutableOp.CreateSet(_, members, _) =>
            members.map(m => m -> false)
          case _: ExecutableOp.WiringOp.ReferenceKey =>
            Seq(target -> false)
          case w: ExecutableOp.WiringOp =>
            w.wiring.associations.map(a => a.key -> a.isByName)
          case w: ExecutableOp.MonadicOp =>
            Seq(w.effectKey -> false)
        }
      case _: ImportDependency =>
        Seq.empty
    }

    val onlyByNameUsages = associations.filter(_._1 == target).forall(_._2)
    onlyByNameUsages
  }

}
