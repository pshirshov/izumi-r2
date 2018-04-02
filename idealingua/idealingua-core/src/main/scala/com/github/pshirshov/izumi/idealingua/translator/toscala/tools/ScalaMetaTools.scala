package com.github.pshirshov.izumi.idealingua.translator.toscala.tools

import scala.meta.{Defn, Init, Stat}

trait ScalaMetaTools {
  implicit class DefnExt[T <: Defn](defn: T) {
    def extendDefinition(stats: Stat*): T = {
      extendDefinition(stats.toList)
    }

    def extendDefinition(stats: List[Stat]): T = {
      val extended = defn match {
        case o: Defn.Object =>
          o.copy(templ = o.templ.copy(stats = o.templ.stats ++ stats))
        case o: Defn.Class =>
          o.copy(templ = o.templ.copy(stats = o.templ.stats ++ stats))
        case o: Defn.Trait =>
          o.copy(templ = o.templ.copy(stats = o.templ.stats ++ stats))
      }
      extended.asInstanceOf[T]
    }

    def addBase(inits: Init*): T = {
      addBase(inits.toList)
    }

    def addBase(inits: List[Init]): T = {
      val extended = defn match {
        case o: Defn.Object =>
          o.copy(templ = o.templ.copy(inits = o.templ.inits ++ inits))
        case o: Defn.Class =>
          o.copy(templ = o.templ.copy(inits = o.templ.inits ++ inits))
        case o: Defn.Trait =>
          o.copy(templ = o.templ.copy(inits = o.templ.inits ++ inits))
      }
      extended.asInstanceOf[T]
    }

  }
}

object ScalaMetaTools extends ScalaMetaTools {

}
