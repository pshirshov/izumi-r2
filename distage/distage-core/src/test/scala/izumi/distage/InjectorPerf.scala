package izumi.distage

import distage.Injector

object InjectorPerf {
  def main(args: Array[String]): Unit = {
    println("[IPT] About to start...")
    for (x <- 0 to 500){
      val t1 = System.nanoTime()
      Injector()
      val t2 = System.nanoTime()
      if (x % 100 == 0) {
        println(s"[IPT]  ${(t2 - t1) / 1000.0 / 1000.0} msec/injector")
      }
    }
    println("[IPT] finished!")

  }
}
