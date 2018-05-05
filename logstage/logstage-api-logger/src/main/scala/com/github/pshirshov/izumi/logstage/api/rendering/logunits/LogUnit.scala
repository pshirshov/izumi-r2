package com.github.pshirshov.izumi.logstage.api.rendering.logunits

import java.time.{Instant, ZoneId}

import com.github.pshirshov.izumi.fundamentals.platform.strings.IzString._
import com.github.pshirshov.izumi.logstage.api.Log
import com.github.pshirshov.izumi.logstage.api.rendering.{ConsoleColors, RenderedMessage}

case class Margin(elipsed: Boolean, size: Int)

sealed trait LogUnit {
  def aliases: Vector[String]

  def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String
}

object LogUnit {

  def withMargin(string: String, margin: Option[Margin]): String = {
    margin match {
      case Some(Margin(true, pad))  => string.ellipsedLeftPad(pad)
      case Some(Margin(_, pad)) => string.leftPad(pad)
      case None => string
    }
  }

  case object ThreadUnit extends LogUnit {
    override val aliases: Vector[String] = Vector("thread", "t")

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      import entry.context
      val builder = new StringBuilder
      val threadName = s"${context.dynamic.threadData.threadName}:${context.dynamic.threadData.threadId}"
      if (withColors) {
        builder.append(Console.UNDERLINED)
      } else {
        builder.append('[')
      }
      builder.append(threadName.ellipsedLeftPad(15))
      if (withColors) {
        builder.append(Console.RESET)
        builder.append(" ")
      } else {
        builder.append("] ")
      }

      withMargin(builder.toString(),margin)

    }

  }

  case object TimestampUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "timestamp", "ts"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      val builder = new StringBuilder
      val context = entry.context
      if (withColors) {
        builder.append(ConsoleColors.logLevelColor(context.dynamic.level))
        builder.append(Console.UNDERLINED)
        builder.append(Console.BOLD)
      }
      val ts = {
        import com.github.pshirshov.izumi.fundamentals.platform.time.IzTime._
        Instant.ofEpochMilli(context.dynamic.tsMillis).atZone(ZoneId.systemDefault()).isoFormat
      }
      builder.append(ts)
      if (withColors) {
        builder.append(Console.RESET)
      }
      withMargin(builder.toString(), margin)
    }
  }

  case object LevelUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "level", "lvl"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      val builder = new StringBuilder
      val context = entry.context
      if (withColors) {
        builder.append(ConsoleColors.logLevelColor(context.dynamic.level))
        builder.append(Console.UNDERLINED)
        builder.append(Console.BOLD)
      }

      val level = context.dynamic.level.toString
      builder.append(String.format(level.substring(0, 1)))
      builder.append(Console.RESET)
      builder.toString()
    }
  }

  case object LocationUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "location", "loc"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      withMargin(s"(${entry.context.static.file}:${entry.context.static.line}) ", margin)

    }
  }

  case object MessageUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "message", "msg"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      formatMessage(entry, withColors).message
    }
  }

  case object CustomContextUnit extends LogUnit {
    override val aliases: Vector[String] = Vector(
      "custom-ctx", "custom"
    )

    override def renderUnit(entry: Log.Entry, withColors: Boolean, margin: Option[Margin] = None): String = {
      import entry.context.customContext.values
      val builder = new StringBuilder(" ")

      if (values.nonEmpty) {
        val customContextString = values.map(formatKv(withColors)).mkString(", ")
        builder.append(s"{$customContextString}")
      }

      builder.toString()
    }
  }

  def apply(alias: String): Option[LogUnit] = {
    all.get(alias)
  }

  private val all = Set(ThreadUnit, TimestampUnit, LevelUnit, LocationUnit, CustomContextUnit, MessageUnit).flatMap {
    unit =>
      unit.aliases.map(_ -> unit)
  }.toMap


  private def formatKv(withColor: Boolean)(kv: (String, Any)): String = {
    if (withColor) {
      s"${Console.GREEN}${kv._1}${Console.RESET}=${Console.CYAN}${kv._2}${Console.RESET}"
    } else {
      s"${kv._1}=${kv._2}"
    }
  }

  private def formatMessage(entry: Log.Entry, withColors: Boolean): RenderedMessage = {
    val templateBuilder = new StringBuilder()
    val messageBuilder = new StringBuilder()
    //    val rawMessageBuilder = new StringBuilder()

    val head = entry.message.template.parts.head
    templateBuilder.append(StringContext.treatEscapes(head))
    messageBuilder.append(StringContext.treatEscapes(head))
    //    rawMessageBuilder.append(head)

    val balanced = entry.message.template.parts.tail.zip(entry.message.args)
    val unbalanced = entry.message.args.takeRight(entry.message.args.length - balanced.length)

    val argToStringColored: Any => String = argValue => argToString(argValue, withColors)

    balanced.foreach {
      case (part, (argName, argValue)) =>
        templateBuilder.append('{')
        templateBuilder.append(argName)
        templateBuilder.append('}')
        templateBuilder.append(StringContext.treatEscapes(part))

        messageBuilder.append(formatKv(withColors)((argName, argToStringColored(argValue))))
        messageBuilder.append(StringContext.treatEscapes(part))

      //        rawMessageBuilder.append('{')
      //        rawMessageBuilder.append(argName)
      //        rawMessageBuilder.append('=')
      //        rawMessageBuilder.append(argToString(argValue))
      //        rawMessageBuilder.append('}')

    }

    unbalanced.foreach {
      case (argName, argValue) =>
        templateBuilder.append("; ?")
        messageBuilder.append("; ")
        messageBuilder.append(formatKv(withColors)((argName, argToStringColored(argValue))))
    }

    RenderedMessage(entry, templateBuilder.toString(), messageBuilder.toString())
  }

  private def argToString(argValue: Any, withColors: Boolean): String = {
    argValue match {
      case e: Throwable =>
        if (withColors) {
          s"${Console.YELLOW}${e.toString}${Console.RESET}"
        } else {
          e.toString
        }

      case _ =>
        argValue.toString
    }
  }
}