package izumi.distage.gc

import distage.DIKey
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.errors.DIError.LoopResolutionError
import izumi.distage.model.definition.{Activation, ModuleDef}
import izumi.distage.model.exceptions.InjectorFailed
import izumi.distage.model.plan.Roots
import org.scalatest.wordspec.AnyWordSpec

class GcBasicTests extends AnyWordSpec with MkGcInjector {
  "Garbage-collecting injector" should {

    "fail during planning on non-by-name loops involving only final classes" in {
      import GcCases.InjectorCase10._

      val injector = mkInjector()
      val exc = intercept[InjectorFailed] {
        injector.plan(
          PlannerInput(
            new ModuleDef {
              make[Circular1]
              make[Circular2]
            },
            Activation.empty,
            Roots(DIKey.get[Circular2]),
          )
        )
      }

      assert(exc.errors.size == 1 && exc.errors.head.isInstanceOf[LoopResolutionError.BestLoopResolutionCannotBeProxied])
    }

    "handle by-name circular dependencies with sets through refs/2" in {
      import GcCases.InjectorCase13._

      val injector = mkNoCglibInjector()
      val plan = injector.plan(
        PlannerInput(
          new ModuleDef {
            make[Circular1]
            make[Circular2]
            make[T1]
            make[Box[T1]].from(new Box(new T1))
          },
          Activation.empty,
          Roots(DIKey.get[Circular1], DIKey.get[Circular2]),
        )
      )

      val result = injector.produce(plan).unsafeGet()

      assert(result.get[Circular1] != null)
      assert(result.get[Circular2] != null)
      assert(result.get[Circular1].q == result.get[Circular2].q)
    }

    "properly handle weak references" in {

      import GcCases.InjectorCase14_GC._

      val roots = Set[DIKey](DIKey.get[Set[Elem]])

      val objects = mkInjector().produce(PlannerInput(module, Activation.empty, roots)).unsafeGet()

      assert(objects.find[Strong].nonEmpty)
      assert(objects.find[Weak].isEmpty)
      assert(objects.get[Set[Elem]].size == 1)

    }
  }
}
