package izumi.distage.injector

import distage._
import izumi.distage.model.PlannerInput
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import org.scalatest.wordspec.AnyWordSpec

class PlanOperationsTest extends AnyWordSpec with MkInjector {

  import PlanOperationsTest._

  private val icKey: DIKey = DIKey.get[IntegrationComponent]
  private val pcKey: DIKey = DIKey.get[PrimaryComponent]

  private val sc0: DIKey = DIKey.get[SharedComponent0]
  private val sc1: DIKey = DIKey.get[SharedComponent1]
  private val sc2: DIKey = DIKey.get[SharedComponent2]

  private val injector = mkInjector()

  "support plan trisplit" in {
    val primary = Set(pcKey)
    val sub = Set(icKey)

    val definition = PlannerInput(
      new ModuleDef {
        make[PrimaryComponent]
        make[IntegrationComponent]
        make[SharedComponent0]
        make[SharedComponent1]
        make[SharedComponent2]
      },
      Activation.empty,
      primary ++ sub,
    )

    val split = injector.ops.trisectByKeys(Activation.empty, definition.bindings, primary) {
      baseplan =>
        assert(sub.intersect(baseplan.keys).isEmpty)
        (sub, Set.empty)
    }

    assert(Set(sc0, sc1, sc2).diff(split.shared.keys).isEmpty)

    assert((primary ++ sub).intersect(split.shared.keys).isEmpty)
    assert(primary.intersect(split.side.keys).isEmpty)
    assert(sub.intersect(split.primary.keys).isEmpty)

    assert(split.primary.keys.intersect(split.side.keys) == Set(sc2))
    assert(split.primary.keys.intersect(split.shared.keys) == Set(sc2))
    assert(split.side.keys.intersect(split.shared.keys) == Set(sc2))
  }

  "support ghost components in trisplit" in {
    val primary = Set(pcKey, icKey)
    val sub = Set(icKey)

    val definition = PlannerInput(
      new ModuleDef {
        make[PrimaryComponent]
        make[IntegrationComponent]
        make[SharedComponent0]
        make[SharedComponent1]
        make[SharedComponent2]
      },
      Activation.empty,
      primary ++ sub,
    )

    val split = injector.ops.trisectByKeys(Activation.empty, definition.bindings, primary)(_ => (sub, Set.empty))

    val sideIndex = split.side
    val primaryIndex = split.primary
    val sharedIndex = split.shared

    assert(primaryIndex.keys.intersect(sideIndex.keys).intersect(sharedIndex.keys) == Set(icKey))

    assert(sharedIndex.keys == Set(sc0, sc1, sc2, icKey))

    assert(sideIndex.keys == Set(icKey))
    assert(sideIndex.plan.meta.nodes.get(icKey).exists(_.isInstanceOf[ImportDependency]))

    assert(primaryIndex.keys == Set(icKey, pcKey, sc2))
    assert(primaryIndex.plan.meta.nodes.get(icKey).exists(_.isInstanceOf[ImportDependency]))
    assert(primaryIndex.plan.meta.nodes.get(sc2).exists(_.isInstanceOf[ImportDependency]))
  }

  "support plan separation" in {
    val primary = Set(pcKey)
    val sub = Set(icKey)

    val definition = PlannerInput(
      new ModuleDef {
        make[PrimaryComponent]
        make[IntegrationComponent]
        make[SharedComponent0]
        make[SharedComponent1]
        make[SharedComponent2]
      },
      Activation.empty,
      primary ++ sub,
    )

    val srcPlan = injector.plan(definition)

    def verifySingleImport(key: DIKey): Unit = {
      val plan = srcPlan.replaceWithImports(Set(key))
      assert(plan.plan.meta.nodes.get(key).exists(_.isInstanceOf[ImportDependency]))
      assert(plan.plan.meta.nodes.values.collect { case i: ImportDependency => i }.size == 1)
      assert(!plan.definition.keys.contains(key))
      ()
    }

    verifySingleImport(icKey)
    verifySingleImport(sc0)
    verifySingleImport(sc1)
  }
}

object PlanOperationsTest {

  case class SharedComponent0()

  case class SharedComponent1(com0: SharedComponent0)

  case class SharedComponent2(com1: SharedComponent1)

  case class IntegrationComponent(sharedComponent1: SharedComponent2)

  case class PrimaryComponent(sharedComponent1: SharedComponent2)

}
