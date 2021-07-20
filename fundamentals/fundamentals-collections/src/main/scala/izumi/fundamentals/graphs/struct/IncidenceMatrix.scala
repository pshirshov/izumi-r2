package izumi.fundamentals.graphs.struct

import scala.collection.compat._
import scala.collection.mutable

final case class IncidenceMatrix[N] private (links: Map[N, Set[N]]) extends AnyVal {

  def transposed: IncidenceMatrix[N] = {
    val output = mutable.HashMap.empty[N, mutable.LinkedHashSet[N]]
    links.foreach {
      case (n, linked) =>
        output.getOrElseUpdate(n, mutable.LinkedHashSet.empty[N])

        linked.foreach {
          l =>
            output.getOrElseUpdate(l, mutable.LinkedHashSet.empty[N]) += n
        }
    }
    new IncidenceMatrix(output.view.mapValues(_.to(Set)).toMap)
  }

  def map[N1](f: N => N1): IncidenceMatrix[N1] = {
    IncidenceMatrix(links.map {
      case (n, deps) =>
        (f(n), deps.map(f))
    })
  }

  def without(nodes: Set[N]): IncidenceMatrix[N] = {
    IncidenceMatrix(links.view.filterKeys(k => !nodes.contains(k)).mapValues(_.diff(nodes)).toMap)
  }

  def rewriteLinked(mapping: Map[N, N]): IncidenceMatrix[N] = {
    IncidenceMatrix(links.view.mapValues(_.map(d => mapping.getOrElse(d, d))).toMap)
  }

  def rewriteAll(mapping: Map[N, N]): IncidenceMatrix[N] = {
    val rewritten = links.map {
      case (n, deps) =>
        val mappedKey = mapping.getOrElse(n, n)
        val mappedValue = deps.map(d => mapping.getOrElse(d, d))
        (mappedKey, mappedValue)
    }
    IncidenceMatrix(rewritten)
  }
}

object IncidenceMatrix {
  def empty[N]: IncidenceMatrix[N] = apply(Map.empty[N, Set[N]])
  def apply[N](links: (N, IterableOnce[N])*): IncidenceMatrix[N] = {
    apply(links.toMap.view.mapValues(_.iterator.toSet).toMap)
  }

  def apply[N](links: Map[N, Set[N]]): IncidenceMatrix[N] = {
    val missing = missingKeys(links)
    val normalized = links ++ missing.map(m => (m, Set.empty[N])).toMap
    new IncidenceMatrix(links ++ normalized)
  }

  def missingKeys[N](links: Map[N, Set[N]]): Set[N] = {
    val allKeys = links.keySet ++ links.values.flatten
    val missing = allKeys -- links.keySet
    missing
  }

  def linear[N](ordered: Seq[N]): IncidenceMatrix[N] = {
    IncidenceMatrix(
      ordered
        .sliding(2)
        .flatMap {
          s =>
            if (s.size > 1) {
              Seq(s.head -> Set(s.last))
            } else {
              Seq.empty
            }
        }.toMap
    )
  }
}
