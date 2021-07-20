package izumi.distage.model.plan.repr

import izumi.distage.model.plan.ExecutableOp.MonadicOp._
import izumi.distage.model.plan.ExecutableOp.ProxyOp._
import izumi.distage.model.plan.ExecutableOp.WiringOp._
import izumi.distage.model.plan.ExecutableOp.{CreateSet, ImportDependency, InstantiationOp, WiringOp, _}
import izumi.distage.model.plan.Wiring.MonadicWiring._
import izumi.distage.model.plan.Wiring.SingletonWiring._
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.operations.OperationOrigin.EqualizedOperationOrigin
import izumi.distage.model.plan.{ExecutableOp, Wiring}
import izumi.distage.model.reflection.{DIKey, LinkedParameter, Provider}
import izumi.fundamentals.platform.strings.IzString._

trait OpFormatter {
  def format(op: ExecutableOp): String
}

object OpFormatter {
  def formatBindingPosition(origin: EqualizedOperationOrigin): String = {
    origin.value match {
      case OperationOrigin.UserBinding(binding) =>
        binding.origin.toString
      case OperationOrigin.SyntheticBinding(binding) =>
        binding.origin.toString
      case OperationOrigin.Unknown =>
        "(<unknown>)"
    }
  }

  def apply(keyFormatter: KeyFormatter, typeFormatter: TypeFormatter, colors: Boolean): OpFormatter = new OpFormatter.Impl(keyFormatter, typeFormatter, colors)

  class Impl(
    keyFormatter: KeyFormatter,
    typeFormatter: TypeFormatter,
    colors: Boolean,
  ) extends OpFormatter
    with DIConsoleColors {
    import keyFormatter.formatKey
    import typeFormatter.formatType

    override protected def colorsEnabled(): Boolean = colors

    override def format(op: ExecutableOp): String = {
      format(op, Set.empty)
    }
    def format(op: ExecutableOp, deferred: Set[DIKey]): String = {
      op match {
        case i: InstantiationOp =>
          i match {
            case CreateSet(target, members, origin) =>
              val repr = doFormat(formatType(target.tpe), members.map(formatKey).toSeq, "newset", ('[', ']'), ('{', '}'))
              val pos = formatBindingPosition(origin)
              formatDefn(target, pos, repr)

            case ExecuteEffect(target, effectKey, _, effectHKTypeCtor, origin) =>
              val pos = formatBindingPosition(origin)
              formatDefn(target, pos, s"${formatOpName("effect")}[$effectHKTypeCtor]${formatKey(effectKey)}")

            case AllocateResource(target, effectKey, _, effectHKTypeCtor, origin) =>
              val pos = formatBindingPosition(origin)
              formatDefn(target, pos, s"${formatOpName("allocate")}[$effectHKTypeCtor]${formatKey(effectKey)}")

            case w: WiringOp =>
              w match {
                case CallProvider(target, wiring, origin) =>
                  formatProviderOp(target, wiring, origin, deferred)
                case UseInstance(target, wiring, origin) =>
                  val pos = formatBindingPosition(origin)
                  if (wiring.instance != null) {
                    formatDefn(target, pos, s"${formatOpName("value")} ${wiring.instance.getClass.getName}#${wiring.instance.hashCode()}")
                  } else {
                    formatDefn(target, pos, formatOpName("null"))
                  }
                case ReferenceKey(target, wiring, origin) =>
                  val pos = formatBindingPosition(origin)
                  formatDefn(target, pos, s"${formatOpName("ref")} ${formatKey(wiring.key)}")
              }
          }

        case ImportDependency(target, references, origin) =>
          val pos = formatBindingPosition(origin)
          val hintBase = if (references.nonEmpty) {
            s"// required for ${references.map(formatKey).mkString(" and ")}"
          } else {
            " // no dependees"
          }

          val hint = styled(hintBase, c.CYAN)
          formatDefn(target, pos, s"${formatOpName("import")} ${formatKey(target)} $hint")

        case p: ProxyOp =>
          p match {
            case MakeProxy(proxied, forwardRefs, origin, byNameAllowed) =>
              val pos = formatBindingPosition(origin)
              val kind = if (byNameAllowed) {
                "proxy.light"
              } else {
                "proxy.cogen"
              }

              val repr =
                s"""${formatOpName(kind, c.RED)} {
                   |${format(proxied, forwardRefs).shift(2)}
                   |}""".stripMargin

              formatDefn(p.target, pos, repr)

            case ProxyOp.InitProxy(target, _, proxy, origin) =>
              val pos = formatBindingPosition(origin)
              val resolved = proxy.forwardRefs.map(formatKey).map(_.shift(2)).mkString("{\n", "\n", "\n}")
              formatDefn(target, pos, s"${formatOpName("init", c.RED)} ${formatKey(proxy.target)} ${styled("with", c.GREEN)} $resolved")

          }
      }
    }

    private def formatProviderOp(target: DIKey, deps: Wiring, origin: EqualizedOperationOrigin, deferred: Set[DIKey]): String = {
      val op = formatProviderWiring(deps, deferred)
      val pos = formatBindingPosition(origin)
      formatDefn(target, pos, op)
    }

    private def formatDefn(target: DIKey, pos: String, repr: String): String = {
      val marker = styled(":=", c.BOLD, c.GREEN)
      val shifted = if (repr.linesIterator.size > 1) {
        s"$marker\n${repr.shift(4)}"
      } else {
        s"$marker $repr"
      }
      s"${formatKey(target)} $pos $shifted"
    }

    private def formatProviderWiring(deps: Wiring, deferred: Set[DIKey]): String = {
      deps match {
        case f: Function =>
          doFormat(formatFunction(f.provider), f.associations.map(formatDependency(deferred)), "call", ('(', ')'), ('{', '}'))

        case other @ (_: Effect | _: Resource | _: Instance | _: Reference) =>
          s"UNEXPECTED WIREABLE: $other"
      }
    }

    private def formatDependency(deferred: Set[DIKey])(association: LinkedParameter): String = {
      // ${RED}defer:($RESET${forwardRefs.map(s => s"${formatKey(s)}").mkString(", ")}$RED)$RESET
      association match {
        case p: LinkedParameter =>
          val fname = if (p.isByName) {
            s"=> ${p.name}"
          } else {
            p.name
          }

          val op = if (deferred.contains(p.key)) {
            styled(s"defer:", c.RED)
          } else {
            ""
          }
          s"""${styled("arg", c.BLUE)} $fname: ${formatType(p.symbol.finalResultType)} <- $op${formatKey(p.key)}"""
      }
    }

    private def formatFunction(provider: Provider): String = {
      s"${provider.funString}(${provider.argTypes.map(formatType).mkString(", ")}): ${formatType(provider.ret)}"
    }

    private def formatOpName(name: String, color: String with Singleton = c.YELLOW) = {
      styled(name, c.UNDERLINED, color)
    }

    private def doFormat(impl: String, depRepr: Seq[String], opName: String, opFormat: (Char, Char), delim: (Char, Char)): String = {
      val sb = new StringBuilder()
      sb.append(s"${formatOpName(opName)}${styled(opFormat._1.toString, c.GREEN)}$impl${c.GREEN}${styled(opFormat._2.toString, c.GREEN)} ${delim._1}")
      if (depRepr.nonEmpty) {
        sb.append("\n")
        sb.append(depRepr.mkString("\n").shift(2))
        sb.append(s"\n${delim._2}")
      } else {
        sb.append(delim._2)
      }

      sb.toString()
    }

  }

}
