package izumi.distage.injector

import distage._
import izumi.distage.constructors.FactoryConstructor
import izumi.distage.fixtures.CircularCases._
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.exceptions.{ProvisioningException, TraitInitializationFailedException}
import org.scalatest.wordspec.AnyWordSpec

class CircularDependenciesTest extends AnyWordSpec with MkInjector {

  "support trait initialization" in {
    import CircularCase2._

    val definition = PlannerInput.everything(new ModuleDef {
      make[CircularBad1]
      make[CircularBad2]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val exc = intercept[ProvisioningException] {
      injector.produce(plan).unsafeGet()
    }

    assert(exc.getSuppressed.head.isInstanceOf[TraitInitializationFailedException])
    assert(exc.getSuppressed.head.getCause.isInstanceOf[RuntimeException])
  }

  "support complex circular dependencies" in {
    import CircularCase2._

    val definition = PlannerInput.everything(new ModuleDef {
      make[Circular3]
      make[Circular1]
      make[Circular2]
      make[Circular5]
      make[Circular4]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    assert(plan.plan.successors.links(DIKey.get[Circular1]).size == 3)
    assert(plan.plan.successors.links(DIKey.ProxyInitKey(DIKey.get[Circular1])).isEmpty)

    assert(plan.plan.predecessors.links(DIKey.get[Circular1]).isEmpty)
    assert(plan.plan.predecessors.links(DIKey.ProxyInitKey(DIKey.get[Circular1])).size == 2)

    val context = injector.produce(plan).unsafeGet()
    val c3 = context.get[Circular3]
    val traitArg = c3.arg

    assert(traitArg != null && traitArg.isInstanceOf[Circular4])
    assert(c3.method == 2L)
    assert(traitArg.testVal == 1)
    assert(context.instances.nonEmpty)
    assert(context.get[Circular4].factoryFun(context.get[Circular4], context.get[Circular5]) != null)
  }

  "Support by-name self-referencing circulars" in {
    import CircularCase3._

    val definition = PlannerInput.everything(new ModuleDef {
      make[ByNameSelfReference]
    })

    val injector = mkNoCglibInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val instance = context.get[ByNameSelfReference]

    assert(instance eq instance.self)
  }

  "Support self-referencing traits" in {
    import CircularCase3._

    val definition = PlannerInput.everything(new ModuleDef {
      make[TraitSelfReference]
    })

    val injector = mkNoCglibInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val instance = context.get[TraitSelfReference]

    assert(instance eq instance.self)
  }

  "Support self-referencing factories for by-name types" in {
    import CircularCase3._

    FactoryConstructor[FactorySelfReference]

    val definition = PlannerInput.everything(new ModuleDef {
      make[ByNameSelfReference]
      make[FactorySelfReference]
    })

    val injector = mkNoCglibInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val instance = context.get[FactorySelfReference]

    assert(instance eq instance.self)

    val instance1 = instance.mkByNameSelfReference(context.get[ByNameSelfReference])
    assert(instance1.self eq context.get[ByNameSelfReference])
    assert(instance1 ne context.get[ByNameSelfReference])

    val instance2 = instance.mkByNameSelfReferenceByName(context.get[ByNameSelfReference])
    assert(instance2.self eq context.get[ByNameSelfReference])
    assert(instance2 ne context.get[ByNameSelfReference])
    assert(instance2 ne instance1)

    var counter = 0
    class CountInstantiations extends ByNameSelfReference(instance1) { counter += 1 }
    instance.mkByNameSelfReferenceByName(new CountInstantiations())
    assert(counter == 0)
  }

  // this test makes no sense because the order is not enforced anymore
//  "Locator.instances returns instances in the order they were created in" in {
//    import CircularCase2._
//
//    val definition = PlannerInput.everything(new ModuleDef {
//      make[Circular3]
//      make[Circular1]
//      make[Circular2]
//      make[Circular5]
//      make[Circular4]
//    })
//
//    val injector = mkInjector()
//    val plan = injector.plan(definition)
//    val context = injector.produce(plan).unsafeGet()
//
//    val planTypes: Seq[SafeType] = plan.steps
//      .collect {
//        case i: InstantiationOp => i
//        case i: MakeProxy => i
//        case i: InitProxy => i
//      }
//      .map(_.target.tpe)
//    val internalArtifacts = Set(SafeType.get[ProxyDispatcher], SafeType.get[LocatorRef])
//    val instanceTypes = context.instances
//      .map(_.key.tpe)
//      .filterNot(internalArtifacts.contains) // remove internal artifacts: proxy stuff, locator ref
//
//    assert(instanceTypes.size == planTypes.size) // proxy creates TWO keys instead of one
//    assert(instanceTypes == planTypes)
//
//    // whitebox test: ensure that plan ops are in a non-lazy collection
//    assert(plan.steps.isInstanceOf[Vector[_]])
//  }

  "support by-name circular dependencies" in {
    import ByNameCycle._

    val definition = PlannerInput.everything(new ModuleDef {
      make[Circular2]
      make[Circular1]
      make[Int].from(1)
    })

    val injector = mkNoCglibInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[Circular1] != null)
    assert(context.get[Circular2] != null)
    assert(context.get[Circular2].testInt == 1)

    assert(context.get[Circular1].isInstanceOf[Circular1])
    assert(context.get[Circular2].isInstanceOf[Circular2])

    assert(context.get[Circular1].test.isInstanceOf[Circular2])
    assert(context.get[Circular2].test.isInstanceOf[Circular1])
  }

  "support generic circular dependencies when generics are erased by type-erasure" in {
    import CircularCase5._

    val definition = PlannerInput.everything(new ModuleDef {
      make[ErasedCircular[Dependency]]
      make[PhantomDependency[Dependency]]
    })

    val injector = mkInjector()
    val context = injector.produce(definition).unsafeGet()

    assert(context.get[ErasedCircular[Dependency]] != null)
    assert(context.get[PhantomDependency[Dependency]] != null)

    assert(context.get[ErasedCircular[Dependency]].dep eq context.get[PhantomDependency[Dependency]])
    assert(context.get[PhantomDependency[Dependency]] eq context.get[ErasedCircular[Dependency]].dep)
  }
}
