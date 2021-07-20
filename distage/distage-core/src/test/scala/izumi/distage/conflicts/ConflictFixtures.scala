package izumi.distage.conflicts

import izumi.distage.model.definition.conflicts.{Annotated, MutSel, Node}
import izumi.distage.model.planning.AxisPoint
import izumi.distage.planning.solver.SemigraphSolver._
import izumi.fundamentals.graphs.NodeShow

object ConflictFixtures {

  final val mutators: SemiEdgeSeq[Annotated[String], String, Int] = SemiEdgeSeq(
    Seq(
      Annotated("app", None, Set.empty) -> Node(Set("s1"), 0),
      Annotated("s1", None, Set(AxisPoint("test", "dev"), AxisPoint("repo", "pg"))) -> Node(Set("shared"), 0),
      Annotated("s1", None, Set(AxisPoint("test", "prod"), AxisPoint("repo", "dynamo"))) -> Node(Set("shared"), 0),
      Annotated("s1", Some(3), Set.empty) -> Node(Set("s1", "s2"), 0),
      Annotated("s1", Some(4), Set(AxisPoint("test", "prod"), AxisPoint("repo", "dynamo"), AxisPoint("fruit", "apple"))) -> Node(Set("s1", "s2"), 0),
      Annotated("s1", Some(5), Set.empty) -> Node(Set("s1", "s2", "d1"), 0),
      Annotated("s1", Some(6), Set(AxisPoint("test", "bad"))) -> Node(Set("s1", "s2"), 0),
      Annotated("d1", None, Set.empty) -> Node(Set("s4"), 1),
      Annotated("s2", None, Set.empty) -> Node(Set("s3"), 1),
      Annotated("s3", None, Set.empty) -> Node(Set("s4"), 1),
    )
  )

  final val withLoop: SemiEdgeSeq[Annotated[String], String, Int] = SemiEdgeSeq(
    mutators.links ++ Seq(
      Annotated("s4", None, Set.empty) -> Node(Set("s1"), 0)
    )
  )

  final val complexMutators: SemiEdgeSeq[Annotated[String], String, Int] = SemiEdgeSeq(
    Seq(
      Annotated("a", None, Set.empty) -> Node(Set.empty[String], 2),
      Annotated("b", None, Set.empty) -> Node(Set("a"), 0),
      Annotated("a", Some(1), Set.empty) -> Node(Set("a"), 0),
      Annotated("a", Some(2), Set.empty) -> Node(Set("b"), 1),
      Annotated("a", Some(3), Set.empty) -> Node(Set("a"), 1),
    )
  )

  implicit object NShow extends NodeShow[WithContext[Int, String]] {
    override def show(t: WithContext[Int, String]): String = {
      s"${t.meta} [[${t.remaps}]]"
    }
  }

  implicit object AShow extends NodeShow[MutSel[String]] {
    override def show(t: MutSel[String]): String = {
      val base = t.key
      t.mut match {
        case Some(value) =>
          s"$base:$value"
        case None =>
          base
      }

    }
  }
}
