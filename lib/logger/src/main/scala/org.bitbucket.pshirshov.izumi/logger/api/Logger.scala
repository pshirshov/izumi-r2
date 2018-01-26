package org.bitbucket.pshirshov.izumi.logger.api

import org.bitbucket.pshirshov.izumi.{InterpolatorMacros, Message}
import org.bitbucket.pshirshov.izumi.logger.{Log, LogConfigService}

// SCALA API
trait Logger extends InterpolatorMacros{

  import Log._


  def log(context: Log.Context, message: Message): Unit = route(Entry(message, context))

  def debug(message: Message)(implicit context: StaticContext, dynamic: ThreadData, custom: CustomContext = EmptyCustomContext): Unit = {
    log(Context(context, DynamicContext(Level.Debug, dynamic), custom), message)
  }

  protected def logConfigService: LogConfigService

  protected def route(entry: Entry): Unit = {
    logConfigService.config(entry).foreach {
      mapping =>
        if (mapping.filter.accept(entry)) {
          mapping.sink.flush(entry)
        }
    }
  }
}

object Logger extends InterpolatorMacros

// don't forget, we need this
trait MacroLogger extends Logger {
  // def debug(template: String, values: Map[String, String]): Unit = macro ...
  // this should perform early filtering, like
  //    if (level >= logConfigService.level(staticContext)) { super.log(...) }
}