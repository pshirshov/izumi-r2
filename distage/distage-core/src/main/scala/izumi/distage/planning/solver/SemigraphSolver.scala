package izumi.distage.planning.solver

import izumi.distage.model.definition.conflicts.ConflictResolutionError.{ConflictingAxisChoices, ConflictingDefs, UnsolvedConflicts}
import izumi.distage.model.definition.conflicts.{ConflictResolutionError, _}
import izumi.distage.model.planning.{ActivationChoices, AxisPoint}
import izumi.distage.planning.solver.SemigraphSolver._
import izumi.functional.IzEither._
import izumi.fundamentals.collections.ImmutableMultiMap
import izumi.fundamentals.collections.IzCollections._
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.{DG, GraphMeta, WeakEdge}

import scala.annotation.{nowarn, tailrec}

/**
  * Combined Garbage Collector, Conflict Resolver and Mutation Resolver
  *
  * Traces the graph from the roots, solves conflict by applying axis rules and
  * orders mutators in sane and predictable order.
  *
  * "predecessor" stands for "a node which should be processed before it's successor".
  *
  * Map of predecessors is a map where key is a dependant and value is a set of all its direct dependencies
  */
trait SemigraphSolver[N, I, V] {
  def resolve(
    predecessors: SemiEdgeSeq[Annotated[N], N, V],
    roots: Set[N],
    activations: Set[AxisPoint],
    weak: Set[WeakEdge[N]],
  ): Either[List[ConflictResolutionError[N, V]], Resolution[N, V]]
}

@nowarn("msg=Unused import")
object SemigraphSolver {
  import scala.collection.compat._
  import scala.collection.immutable

  final case class RemappedValue[V, N](meta: V, remapped: Map[N, MutSel[N]])
  final case class SemiEdgeSeq[D, N, V](links: Seq[(D, Node[N, V])]) extends AnyVal
  final case class SemiIncidenceMatrix[D, N, V](links: Map[D, Node[N, V]]) extends AnyVal

  final case class Selected[N](key: N, axis: Set[AxisPoint])

  final case class Resolution[N, V](graph: DG[MutSel[N], RemappedValue[V, N]]) //, unresolved: Map[Annotated[N], Seq[Node[N, V]]])
  private final case class ResolvedMutations[N](
    lastOp: MutSel[N],
    operationReplacements: Seq[(MutSel[N], Set[MutSel[N]])],
    outerKeyReplacements: Seq[(MutSel[N], (N, MutSel[N]))],
  )

  private final case class ClassifiedConflicts[A](mutators: Set[A], defns: Set[A])
  private final case class MainResolutionStatus[N, V](resolved: SemiIncidenceMatrix[Annotated[N], N, V], unresolved: Map[Annotated[N], Seq[Node[N, V]]])
  final case class WithContext[V, N](meta: V, remaps: Map[N, MutSel[N]])

  class SemigraphSolverImpl[N, I, V] extends SemigraphSolver[N, I, V] {

    override def resolve(
      predecessors: SemiEdgeSeq[Annotated[N], N, V],
      roots: Set[N],
      activations: Set[AxisPoint],
      weak: Set[WeakEdge[N]],
    ): Either[List[ConflictResolutionError[N, V]], Resolution[N, V]] = {
      for {
        semiMatrix <- resolveAxis(predecessors, roots, weak, activations)
        unsolvedConflicts = semiMatrix.links.keySet.groupBy(a => MutSel(a.key, a.mut)).filter(_._2.size > 1)
        _ <-
          if (unsolvedConflicts.nonEmpty) {
            Left(List(UnsolvedConflicts(unsolvedConflicts)))
          } else {
            Right(())
          }
        deannotated = semiMatrix.links.map {
          case (k, v) =>
            (k.withoutAxis, Node(v.deps.map(_.key), v.meta))
        }
        mutationsResolved <- resolveMutations(SemiIncidenceMatrix(deannotated))
      } yield {
        assert(semiMatrix.links.keySet.groupBy(_.withoutAxis).forall(_._2.size == 1))
        val meta = deannotated.map {
          case (k, v) =>
            val targetKey = mutationsResolved.indexRemap.getOrElse(k, k)
            val replMap = mutationsResolved.outerReplMap.getOrElse(targetKey, Map.empty)
            (targetKey, RemappedValue(v.meta, replMap))
        }

        Resolution(DG.fromPred(mutationsResolved.finalMatrix, GraphMeta(meta))) //, resolved.unresolved)
      }
    }

    protected def resolveAxis(
      predecessors: SemiEdgeSeq[Annotated[N], N, V],
      roots: Set[N],
      weak: Set[WeakEdge[N]],
      activations: Set[AxisPoint],
    ): Either[List[ConflictResolutionError[N, V]], SemiIncidenceMatrix[Annotated[N], Selected[N], V]] = {
      for {
        _ <- nonAmbigiousActivations(activations)
        activationChoices = ActivationChoices(activations)
        onlyCorrect <- traceGrouped(activationChoices, weak)(roots, roots, predecessors, Map.empty)
      } yield {

        val nonAmbiguous = onlyCorrect.filterNot(_._1.isMutator).map { case (k, _) => (k.key, k.axis) }
        val result = onlyCorrect.map {
          case (key, node) =>
            (key, Node(node.deps.map(d => Selected(d, nonAmbiguous.getOrElse(d, Set.empty))), node.meta))
        }
        SemiIncidenceMatrix(result)
      }
    }

    protected def nonAmbigiousActivations(
      activations: Set[AxisPoint]
    ): Either[List[ConflictingAxisChoices[N]], Unit] = {
      val bad = activations.groupBy(_.axis).filter(_._2.size > 1)
      if (bad.isEmpty)
        Right(())
      else
        Left(List(ConflictingAxisChoices(bad)))
    }

    @tailrec
    private def traceGrouped(
      activations: ActivationChoices,
      weak: Set[WeakEdge[N]],
    )(roots: Set[N],
      reachable: Set[N],
      predecessors: SemiEdgeSeq[Annotated[N], N, V],
      currentResult: Map[Annotated[N], Node[N, V]],
    ): Either[List[ConflictResolutionError[N, V]], Map[Annotated[N], Node[N, V]]] = {
      val out: Either[List[ConflictResolutionError[N, V]], Seq[Iterable[(Annotated[N], Node[N, V])]]] = roots.toSeq.flatMap {
        root =>
          val (mutators, definitions) =
            predecessors.links.toMultiNodeMap
              .getOrElse(root, Set.empty)
              .partition(n => n._1.isMutator)

          val (goodMutators, _) = filterDeactivated(activations, mutators.toSeq)
          Seq(Right(goodMutators), resolveConflict(activations, definitions.toSeq))
      }.biAggregate

      val nxt = for {
        nextResult <- out.map(_.flatten.toMap)
        nextDeps =
          nextResult.toSeq
            .flatMap {
              case (successor, node) =>
                node.deps.map(d => (successor, d))
            }
            .filterNot {
              case (successor, predecessor) =>
                weak.contains(WeakEdge(predecessor, successor.key))
            }
            .map(_._2)
      } yield {
        (nextResult, nextDeps)
      }

      nxt match {
        case Left(value) =>
          Left(value)
        case Right((nextResult, nextDeps)) if nextDeps.isEmpty =>
          Right(currentResult ++ nextResult)
        case Right((nextResult, nextDeps)) =>
          traceGrouped(activations, weak)(nextDeps.toSet.diff(reachable), reachable ++ roots, predecessors, currentResult ++ nextResult)
      }
    }

    protected def resolveConflict(
      activations: ActivationChoices,
      conflict: Seq[(Annotated[N], Node[N, V])],
    ): Either[List[ConflictResolutionError[N, V]], Map[Annotated[N], Node[N, V]]] = {

      val (onlyValid, /*invalid*/ _) = filterDeactivated(activations, conflict)

      NonEmptyList.from(onlyValid) match {
        case Some(conflict) =>
          // keep in mind: only `invalid` contains elements which are known to be inactive (there is a conflicting axis point)
          if (conflict.toList.sizeIs == 1) {
            Right(Map(conflict.head))
          } else {
            val hasAxis = conflict.toList.filter(_._1.axis.nonEmpty)
            hasAxis match {
              case head :: Nil =>
                if (activations.allConfigured(head._1.axis)) {
                  Right(Map(head))
                } else {
                  Left(List(conflictingDefsError(conflict)))
                }
              case _ =>
                chooseMostSpecificInConflict(activations, conflict)
            }
          }
        case None =>
          Right(Map.empty)
      }

    }

    protected def filterDeactivated(
      activations: ActivationChoices,
      conflict: Seq[(Annotated[N], Node[N, V])],
    ): (Seq[(Annotated[N], Node[N, V])], Seq[(Annotated[N], Node[N, V])]) = {
      conflict.partition { case (k, _) => activations.allValid(k.axis) }
    }

    // activation is better (more specific) than another if it contains all of its axis points and more,
    // however, activations cannot be compared if they contain opposite axis points along any axis
    protected def chooseMostSpecificInConflict(
      activations: ActivationChoices,
      conflict: NonEmptyList[(Annotated[N], Node[N, V])],
    ): Either[List[ConflictingDefs[N, V]], Map[Annotated[N], Node[N, V]]] = {
      val sorted = conflict.toList.sortWith((a, b) => b._1.axis.subsetOf(a._1.axis))
      val mostSpecific = sorted.head
      val mostSpecificAxis = mostSpecific._1.axis
      val others = sorted.tail

      val allAreComparableAndStrictlyLess = others.iterator.map(_._1.axis).forall {
        cAxis =>
          cAxis != mostSpecificAxis && cAxis.subsetOf(mostSpecificAxis) &&
          (cAxis ++ mostSpecificAxis).groupBy(_.axis).forall(_._2.sizeIs == 1)
      }

      // most specific binding must have fully-configured axis,
      // otherwise there is an ambiguity with all the more specific bindings
      // that will override this one if new choices appear
      if (allAreComparableAndStrictlyLess && mostSpecificAxis.nonEmpty && activations.allConfigured(mostSpecificAxis)) {
        Right(Map(mostSpecific))
      } else {
        Left(List(conflictingDefsError(conflict)))
      }
    }

    protected def conflictingDefsError(conflict: NonEmptyList[(Annotated[N], Node[N, V])]): ConflictingDefs[N, V] = {
      ConflictingDefs(conflict.toList.map { case (k, n) => k.withoutAxis -> (k.axis -> n) }.toMultimap)
    }

    private def resolveMutations(predecessors: SemiIncidenceMatrix[MutSel[N], N, V]): Either[List[Nothing], Result] = {
      val conflicts = predecessors.links.keySet
        .groupBy(_.key)
        .filter(_._2.size > 1)

      val targets = conflicts.map {
        case (k, nn) =>
          val (mutators, definitions) = nn.partition(_.isMutator)
          (k, ClassifiedConflicts(mutators, definitions))
      }

      val resolved = targets.flatMap {
        case (t, c) =>
          doResolve(predecessors, t, c)
      }

      val allRepls = resolved.flatMap(_._2.operationReplacements)
      val finalElements = resolved.view.mapValues(_.lastOp).toMap

      val rewritten = predecessors.links.map {
        case (n, deps) =>
          val mapped = deps.deps.map {
            d =>
              val asAnn = MutSel(d, None)
              finalElements.getOrElse(asAnn, asAnn)
          }
          (n, mapped)
      }

      val result = IncidenceMatrix(rewritten ++ allRepls)
      val indexRemap: Map[MutSel[N], MutSel[N]] = finalElements.values.flatMap {
        f =>
          Seq((f, MutSel(f.key, None))) ++ result.links.keySet.filter(_.key == f.key).filterNot(_ == f).zipWithIndex.map {
            case (k, idx) =>
              (k, MutSel(k.key, Some(idx)))
          }
      }.toMap

      val allOuterReplacements = resolved.map(_._2.outerKeyReplacements).toSeq.flatten
      //assert(allOuterReplacements.groupBy(_._1).forall(_._2.size == 1))
      val outerReplMap = allOuterReplacements.toMap
        .map {
          case (k, (v, r)) =>
            (indexRemap.getOrElse(k, k), v, indexRemap.getOrElse(r, r))
        }
        .groupBy(_._1)
        .map {
          case (context, remaps) =>
            val cleaned: immutable.Iterable[(N, MutSel[N])] = remaps.map { case (_, k, t) => (k, t) }
            assert(cleaned.groupBy(_._1).forall(_._2.size < 2))
            (context, cleaned.toMap)
        }

      val finalMatrix: IncidenceMatrix[MutSel[N]] = result.rewriteAll(indexRemap)
      Right(Result(finalMatrix, indexRemap, outerReplMap))
    }

    private case class Result(
      finalMatrix: IncidenceMatrix[MutSel[N]],
      indexRemap: Map[MutSel[N], MutSel[N]],
      outerReplMap: Map[MutSel[N], Map[N, MutSel[N]]],
    )

    private def doResolve(
      predecessors: SemiIncidenceMatrix[MutSel[N], N, V],
      target: N,
      classified: ClassifiedConflicts[MutSel[N]],
    ): Option[(MutSel[N], ResolvedMutations[N])] = {
      val asFinalTarget = MutSel(target, None)

      val asSeq = classified.mutators.toSeq
      val filtered = classified.defns.filter(d => d.key == target)
      val firstOp = filtered.headOption.getOrElse(asFinalTarget)

      asSeq.headOption match {
        case Some(lastOp) =>
          // TODO: here we may sort mutators according to some rule
          val mutationsInApplicationOrder = asSeq.filterNot(_ == lastOp)
          val withFinalAtTheEnd = mutationsInApplicationOrder ++ Seq(lastOp)

          var first: MutSel[N] = firstOp
          val replacements = withFinalAtTheEnd.map {
            m =>
              val replacement = first
              first = m

              val rewritten = predecessors.links(m).deps.map {
                ld: N =>
                  if (ld == target) {
                    replacement
                  } else {
                    MutSel(ld, None)
                  }
              }

              (m, rewritten, (target, replacement))
          }

          val innerReplacements = replacements.map { case (a, _, c) => (a, c) }
          val outerReplacements = replacements.map { case (a, b, _) => (a, b) }
          Some((asFinalTarget, ResolvedMutations(lastOp, outerReplacements, innerReplacements)))
        case None =>
          None
      }
    }

  }

  implicit class SemiGraphOps[N, V](private val matrix: Seq[(Annotated[N], Node[N, V])]) extends AnyVal {
    def toMultiNodeMap: ImmutableMultiMap[N, (Annotated[N], Node[N, V])] = matrix.iterator.map { case (key, node) => (key.key, (key, node)) }.toMultimap
  }

}
