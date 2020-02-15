package izumi.distage.injector

import distage.{ModuleDef, PlannerInput}
import izumi.distage.fixtures.BasicCases.BasicCase1
import izumi.distage.fixtures.SetCases.SetCase2
import izumi.distage.model.exceptions.TODOBindingException
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Try


class AdvancedBindingsTest extends AnyWordSpec with MkInjector {

  "Support TODO bindings" in {
    import BasicCase1._

    val injector = mkInjector()

    val def1 = PlannerInput.noGc(new ModuleDef {
      todo[TestDependency0]
    })
    val def2 = PlannerInput.noGc(new ModuleDef {
      make[TestDependency0].todo
    })
    val def3 = PlannerInput.noGc(new ModuleDef {
      make[TestDependency0].named("fug").todo
    })

    val plan1 = injector.plan(def1)
    val plan2 = injector.plan(def2)
    val plan3 = injector.plan(def3)

    assert(Try(injector.produce(plan1).unsafeGet()).toEither.left.exists(_.getSuppressed.head.isInstanceOf[TODOBindingException]))
    assert(Try(injector.produce(plan2).unsafeGet()).toEither.left.exists(_.getSuppressed.head.isInstanceOf[TODOBindingException]))
    assert(Try(injector.produce(plan3).unsafeGet()).toEither.left.exists(_.getSuppressed.head.isInstanceOf[TODOBindingException]))
  }

  "Set element references are the same as their referees" in {
    import SetCase2._

    val definition = PlannerInput.noGc(new ModuleDef {
      make[Service1]

      many[Service]
        .ref[Service1]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan).unsafeGet()
    val svc = context.get[Service1]
    val set = context.get[Set[Service]]
    assert(set.head eq svc)
  }

}



