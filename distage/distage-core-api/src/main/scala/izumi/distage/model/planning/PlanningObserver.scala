package izumi.distage.model.planning

import izumi.distage.model.PlannerInput
import izumi.distage.model.plan.ExecutableOp
import izumi.distage.model.reflection.DIKey
import izumi.fundamentals.graphs.DG
import izumi.fundamentals.platform.language.unused

/**
  * Execute side-effects to observe planning algorithm execution, e.g. log, write GraphViz files, etc.
  *
  * @see GraphDumpObserver
  */
trait PlanningObserver {
  def onPlanningFinished(@unused input: PlannerInput, @unused plan: DG[DIKey, ExecutableOp]): Unit = {}
}
