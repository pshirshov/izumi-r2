package izumi.distage.model.plan.repr

import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.operations.OperationOrigin.EqualizedOperationOrigin
import izumi.distage.model.plan.topology.DepTreeNode
import izumi.distage.model.plan.topology.DepTreeNode._
import izumi.distage.model.plan.topology.DependencyGraph.DependencyKind
import izumi.distage.model.reflection._

import scala.collection.mutable

class DepTreeRenderer(node: DepNode, index: Map[DIKey, ExecutableOp]) {
  val minimizer = new KeyMinimizer(collectKeys(node), DIRendering.colorsEnabled)

  def render(): String = render(node)

  /**
    * May be unsafe to call on big graphs
    */
  private def render(node: DepTreeNode): String = {
    val prefix = " " * 2 * node.level

    node match {
      case _: Truncated =>
        prefix + "...\n"
      case node: CircularReference =>
        s"$prefix⥀ ${node.level}: ${renderKey(node.key)}\n"
      case node: DepNode =>
        val sb = new mutable.StringBuilder()
        val symbol = if (node.graph.kind == DependencyKind.Depends) {
          "⮑"
        } else {
          "↖"
        }

        if (node.level > 0) {
          sb.append(prefix)
          sb.append(s"$symbol ${node.level}: ${renderKey(node.key)}")
        } else {
          sb.append(s"➤ ${renderKey(node.key)}")
        }
        sb.append("\n")

        node.children.foreach {
          c =>
            sb.append(render(c))
        }

        sb.toString()

    }
  }

  private def renderOrigin(origin: EqualizedOperationOrigin): String = {
    origin.value match {
      case OperationOrigin.UserBinding(binding) =>
        binding.origin.toString
      case OperationOrigin.SyntheticBinding(binding) =>
        binding.origin.toString
      case OperationOrigin.Unknown =>
        ""
    }
  }

  private def renderKey(key: DIKey): String = {
    s"${minimizer.renderKey(key)} ${index.get(key).map(op => renderOrigin(op.origin)).getOrElse("")}"
  }

  private def collectKeys(node: DepTreeNode): Set[DIKey] = {
    node match {
      case _: Truncated =>
        Set.empty
      case r: CircularReference =>
        Set(r.key)
      case n: DepNode =>
        Set(n.key) ++ n.children.flatMap(collectKeys)
    }
  }

}
