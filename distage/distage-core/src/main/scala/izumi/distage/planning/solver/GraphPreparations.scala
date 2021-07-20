package izumi.distage.planning.solver

import izumi.distage.model.definition.BindingTag.AxisTag
import izumi.distage.model.definition.conflicts.{Annotated, Node}
import izumi.distage.model.definition.{Binding, ModuleBase}
import izumi.distage.model.plan.ExecutableOp.{CreateSet, InstantiationOp, MonadicOp, WiringOp}
import izumi.distage.model.plan.{ExecutableOp, Roots, Wiring}
import izumi.distage.model.planning.AxisPoint
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.BindingTranslator
import izumi.distage.planning.solver.SemigraphSolver.SemiEdgeSeq
import izumi.fundamentals.graphs.WeakEdge
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.fundamentals.graphs.tools.gc.Tracer

import scala.annotation.nowarn

@nowarn("msg=Unused import")
class GraphPreparations(
  bindingTranslator: BindingTranslator
) {

  import scala.collection.compat._

  def findWeakSetMembers(
    sets: Map[Annotated[DIKey], Node[DIKey, InstantiationOp]],
    matrix: SemiEdgeSeq[Annotated[DIKey], DIKey, InstantiationOp],
    roots: Set[DIKey],
  ): Set[WeakEdge[DIKey]] = {
    import izumi.fundamentals.collections.IzCollections._

    val indexed = matrix.links.map {
      case (successor, node) =>
        (successor.key, node.meta)
    }.toMultimapMut

    val setDefs = sets
      .collect {
        case (target, Node(_, s: CreateSet)) =>
          (target, s.members)
      }

    setDefs.flatMap {
      case (_, members) =>
        members
          .diff(roots)
          .flatMap {
            member =>
              indexed.get(member).toSeq.flatten.collect {
                case ExecutableOp.WiringOp.ReferenceKey(_, Wiring.SingletonWiring.Reference(_, referenced, true), _) =>
                  WeakEdge(referenced, member)
              }
          }
    }.toSet
  }

  def getRoots(input: Roots, allOps: Seq[(Annotated[DIKey], InstantiationOp)]): Set[DIKey] = {
    val effective = input match {
      case Roots.Of(roots) =>
        // TODO: should we remove roots which are retained by effective roots? see #1476
        roots.toSet
      case Roots.Everything =>
        import izumi.fundamentals.collections.IzCollections._
        // this somehow duplicates plan.noSuccessors, though this happens BEFORE planning
        val dependees = allOps.flatMap {
          case (k, op) =>
            val reqs = op match {
              case op: CreateSet =>
                op.members
              case op: WiringOp =>
                op.wiring.requiredKeys
              case op: MonadicOp =>
                Set(op.effectKey)
            }
            reqs.map(r => (r, Option(k.key))) ++ Set((k.key, None: Option[DIKey]))
        }.toMultimap

        val noDependencies = dependees.filter(_._2.forall(_.isEmpty)).keySet

        val depmatrix = IncidenceMatrix(dependees.map { case (prev, succs) => (prev, succs.flatten) })
        val reachable = new Tracer[DIKey]().trace(depmatrix, Set.empty, noDependencies)

        val allKeys = allOps.map(_._1.key).toSet
        (allKeys -- reachable) ++ noDependencies
    }
    effective
  }

  def toDeps(allOps: Seq[(Annotated[DIKey], InstantiationOp)]): Seq[(Annotated[DIKey], Node[DIKey, InstantiationOp])] = {
    allOps.collect {
      case (target, op: WiringOp) => (target, toDep(op))
      case (target, op: MonadicOp) => (target, toDep(op))
    }
  }

  def toDep: PartialFunction[InstantiationOp, Node[DIKey, InstantiationOp]] = {
    case op: WiringOp =>
      Node(op.wiring.requiredKeys, op: InstantiationOp)
    case op: MonadicOp =>
      Node(Set(op.effectKey), op: InstantiationOp)
  }

  def computeOperationsUnsafe(bindings: ModuleBase): Iterator[(Annotated[DIKey], InstantiationOp, Binding)] = {
    bindings.iterator
      // this is a minor optimization but it makes some conflict resolution strategies impossible
      //.filter(b => activationChoices.allValid(toAxis(b)))
      .flatMap {
        b =>
          val next = bindingTranslator.computeProvisioning(b)
          (next.provisions ++ next.sets.values).map((b, _))
      }
      .zipWithIndex
      .map {
        case ((b, n), idx) =>
          val mutIndex = b match {
            case Binding.SingletonBinding(_, _, _, _, true) =>
              Some(idx)
            case _ =>
              None
          }

          val axis = n match {
            case _: CreateSet =>
              Set.empty[AxisPoint] // actually axis marking makes no sense in case of sets
            case _ =>
              getAxisPoints(b)
          }

          (Annotated(n.target, mutIndex, axis), n, b)
      }
  }

  def computeSetsUnsafe(allOps: Seq[(Annotated[DIKey], InstantiationOp)]): Iterator[(DIKey, (CreateSet, Set[DIKey]))] = {
    allOps.view
      .collect { case (target, op: CreateSet) => (target, op) }
      .groupBy {
        case (a, _) =>
          assert(a.mut.isEmpty)
          assert(a.axis.isEmpty, a.toString)
          a.key
      }
      .view
      .mapValues(_.map(_._2))
      .mapValues {
        ops =>
          val firstOp = ops.head
          val potentialMembers = ops.tail.foldLeft(ops.head.members) {
            case (acc, op) =>
              acc ++ op.members
          }
          (firstOp.copy(members = potentialMembers), potentialMembers)
      }
      .iterator
  }

  protected[this] def getAxisPoints(b: Binding): Set[AxisPoint] = {
    b.tags.collect {
      case AxisTag(axisValue) =>
        axisValue.toAxisPoint
    }
  }
}
