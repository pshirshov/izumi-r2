package izumi.distage.injector

import distage._
import izumi.distage.fixtures.CircularCases._
import izumi.distage.fixtures.InnerClassCases.{InnerClassStablePathsCase, InnerClassUnstablePathsCase}
import izumi.distage.fixtures.ResourceCases.{CircularResourceCase, Ref, Suspend2}
import izumi.distage.injector.ResourceEffectBindingsTest.Fn
import izumi.distage.model.exceptions.ProvisioningException
import izumi.distage.model.plan.Roots
import izumi.distage.provisioning.strategies.cglib.exceptions.CgLibInstantiationOpException
import izumi.fundamentals.platform.functional.Identity
import net.sf.cglib.core.CodeGenerationException
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.Queue

class CglibProxiesTestJvm extends AnyWordSpec with MkInjector {

  "CircularDependenciesTest" should {

    "support circular dependencies" in {
      import CircularCase1._

      val definition = PlannerInput.everything(new ModuleDef {
        make[Circular2]
        make[Circular1]
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Circular1] != null)
      assert(context.get[Circular2] != null)
      assert(context.get[Circular2].arg != null)
    }

    "support circular dependencies with final class implementations" in {
      import CircularCase1._

      val definition = PlannerInput.everything(new ModuleDef {
        make[Circular2].from[Circular2Impl]
        make[Circular1].from[Circular1Impl]
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Circular1] != null)
      assert(context.get[Circular2] != null)
      assert(context.get[Circular2].arg != null)
    }

    "support circular dependencies in providers" in {
      import CircularCase1._

      val definition = PlannerInput.everything(new ModuleDef {
        make[Circular2].from {
          c: Circular1 => new Circular2(c)
        }
        make[Circular1].from {
          c: Circular2 =>
            val a = new Circular1 {
              override val arg: Circular2 = c
            }
            a
        }
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Circular1] != null)
      assert(context.get[Circular2] != null)
      assert(context.get[Circular2].arg != null)
    }

    "Supports self-referencing circulars" in {
      import CircularCase3._

      val definition = PlannerInput.everything(new ModuleDef {
        make[SelfReference]
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan).unsafeGet()

      val instance = context.get[SelfReference]

      assert(instance eq instance.self)
    }

    "Support self-referencing provider" in {
      import CircularCase3._

      val definition = PlannerInput.everything(new ModuleDef {
        make[SelfReference].from {
          self: SelfReference =>
            new SelfReference(self)
        }
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan).unsafeGet()

      val instance = context.get[SelfReference]

      assert(instance eq instance.self)
    }

    "support proxy circular dependencies involving a primitive type" in {
      import CircularCase8._

      val definition = PlannerInput.everything(new ModuleDef {
        make[Circular2]
        make[Circular1]
        make[Int].from(1)
      })

      val injector = mkInjector()
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

    "support circular dependencies that use another object in their constructor that isn't involved in a cycle" in {
      import CircularCase9._

      val definition = PlannerInput.everything(new ModuleDef {
        make[Circular2]
        make[Circular1]
        make[IntHolder]
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[Circular1] != null)
      assert(context.get[Circular2] != null)
      assert(context.get[Circular1].int1 == 2)
      assert(context.get[Circular2].int2 == 3)

      assert(context.get[Circular1].isInstanceOf[Circular1])
      assert(context.get[Circular2].isInstanceOf[Circular2])

      assert(context.get[Circular1].test.isInstanceOf[Circular2])
      assert(context.get[Circular2].test.isInstanceOf[Circular1])
    }

    "support fully generic circular dependencies" in {
      import CircularCase5._

      val definition = PlannerInput.everything(new ModuleDef {
        make[GenericCircular[Dependency]]
        make[Dependency]
      })

      val injector = mkInjector()
      val context = injector.produce(definition).unsafeGet()

      assert(context.get[Dependency] != null)
      assert(context.get[GenericCircular[Dependency]] != null)

      assert(context.get[Dependency] eq context.get[GenericCircular[Dependency]].dep)
      assert(context.get[GenericCircular[Dependency]] eq context.get[Dependency].dep)
    }

    "support named circular dependencies" in {
      import CircularCase4._

      val definition = PlannerInput.everything(new ModuleDef {
        make[IdTypeCircular]
        make[IdParamCircular]
        make[Dependency[IdTypeCircular]].named("special")
        make[Dependency[IdParamCircular]].named("special")
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan).unsafeGet()

      assert(context.get[IdTypeCircular] != null)
      assert(context.get[IdParamCircular] != null)

      assert(context.get[IdParamCircular] eq context.get[Dependency[IdParamCircular]]("special").dep)
      assert(context.get[IdTypeCircular] eq context.get[Dependency[IdTypeCircular]]("special").dep)
    }

    "support type refinements in circular dependencies" in {
      import CircularCase6._

      val definition = PlannerInput.everything(new ModuleDef {
        make[Dependency { def dep: RefinedCircular }].from[RealDependency]
        make[RefinedCircular]
      })

      val injector = mkInjector()
      val context = injector.produce(definition).unsafeGet()

      assert(context.get[Dependency { def dep: RefinedCircular }] != null)
      assert(context.get[RefinedCircular] != null)

      assert(context.get[RefinedCircular] eq context.get[Dependency { def dep: RefinedCircular }].dep)
      assert(context.get[Dependency { def dep: RefinedCircular }] eq context.get[RefinedCircular].dep)
    }

    "support simple by-name forward ref when there are non-by-name references" in {
      import CircularCase10._

      val definition = PlannerInput(
        new ModuleDef {
          make[Component1]
          make[Component2]
          make[ComponentWithByNameFwdRef]
          make[ComponentHolder]
          make[Root]
        },
        Activation.empty,
        Roots(DIKey.get[Root]),
      )

      val injector = mkInjector()
      val context = injector.produce(definition).unsafeGet()

      assert(context.get[ComponentHolder].componentFwdRef eq context.get[ComponentWithByNameFwdRef])
      assert(context.get[ComponentWithByNameFwdRef].get eq context.get[ComponentHolder])
      assert(context.get[Root].holder eq context.get[ComponentHolder])
    }

    "Regression test 1: isolated cycles causing spooky action at a distance" in {
      import CircularCase7._

      val definition = PlannerInput.everything(new ModuleDef {
        // cycle
        make[DynamoDDLService]
        make[DynamoComponent]
        make[DynamoClient]
        //

        make[Sonar]
        make[ComponentsLifecycleManager]
        make[RoleStarter]
        make[TgHttpComponent]
        make[HttpServerLauncher]
        make[IRTServerBindings]
        make[IRTClientMultiplexor]
        make[ConfigurationRepository]
        make[DynamoQueryExecutorService]
        make[IRTMultiplexorWithRateLimiter]
        make[HealthCheckService]
        make[HealthCheckHttpRoutes]
        make[K8ProbesHttpRoutes]

        many[IRTWrappedService]
        many[IRTWrappedClient]
        many[WsSessionListener[String]]
        many[HealthChecker]
        many[TGLegacyRestService]
        many[DynamoDDLGroup]
      })

      val injector = Injector[Identity](
        AutoSetModule()
          .register[RoleComponent]
          .register[RoleService]
          .register[IntegrationComponent]
          .register[AutoCloseable]
      )

      val plan = injector.plan(definition)
      println(plan.render())
      injector.produce(plan).unsafeGet()
    }
  }

  "InnerClassesTest" should {

    "progression test: can't find proper constructor for circular dependencies inside stable objects that contain inner classes from inherited traits that depend on types defined inside trait" in {
      val res = intercept[ProvisioningException] {
        import InnerClassStablePathsCase._
        import StableObjectInheritingTrait._

        val definition = PlannerInput.everything(new ModuleDef {
          make[Circular1]
          make[Circular2]
        })

        val context = mkInjector().produce(definition).unsafeGet()

        assert(context.get[Circular1] != null)
        assert(context.get[Circular1].circular2 != context.get[Circular2])
      }
      assert(res.getMessage.contains("Failed to instantiate class with CGLib, make sure you don't dereference proxied parameters in constructors"))
    }

    "progression test: cglib proxies can't resolve circular path-dependent dependencies (we don't take prefix type into account when calling constructor for generated lambdas and end up choosing the wrong constructor...)" in {
      // the value prefix probably has to be stored inside the Provider to fix this
      val exc = intercept[ProvisioningException] {
        import InnerClassUnstablePathsCase._
        val testProviderModule = new TestModule

        val definition = PlannerInput.everything(new ModuleDef {
          //        make[testProviderModule.type].from[testProviderModule.type](testProviderModule: testProviderModule.type)
          make[testProviderModule.Circular1]
          make[testProviderModule.Circular2]
        })

        val context = mkInjector().produce(definition).unsafeGet()

        assert(context.get[testProviderModule.TestFactory].mk(testProviderModule.TestDependency()) == testProviderModule.TestClass(testProviderModule.TestDependency()))
      }
      assert(exc.getSuppressed.head.isInstanceOf[CgLibInstantiationOpException])
      assert(exc.getSuppressed.head.getCause.isInstanceOf[CodeGenerationException])
      assert(exc.getSuppressed.head.getCause.getCause.isInstanceOf[NoSuchMethodException])
    }

  }

  "ResourceEffectBindingsTest" should {

    "Support self-referencing circular effects" in {
      import izumi.distage.fixtures.CircularCases.CircularCase3._

      val definition = PlannerInput.everything(new ModuleDef {
        make[Ref[Fn, Boolean]].fromEffect(Ref[Fn](false))
        make[SelfReference].fromEffect {
          (ref: Ref[Fn, Boolean], self: SelfReference) =>
            ref.update(!_).flatMap(_ => Suspend2(new SelfReference(self)))
        }
      })

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produceCustomF[Suspend2[Throwable, _]](plan).unsafeGet().unsafeRun()

      val instance = context.get[SelfReference]

      assert(instance eq instance.self)
      assert(context.get[Ref[Fn, Boolean]].get.unsafeRun())
    }

    "Support mutually-referent circular resources" in {
      import CircularResourceCase._
      import ResourceEffectBindingsTest.Fn

      val definition = PlannerInput(
        new ModuleDef {
          make[Ref[Fn, Queue[Ops]]].fromEffect(Ref[Fn](Queue.empty[Ops]))
          many[IntegrationComponent]
            .ref[S3Component]
          make[S3Component].fromResource(s3ComponentResource[Fn] _)
          make[S3Client].fromResource(s3clientResource[Fn] _)
        },
        Activation.empty,
        Roots(DIKey.get[S3Client]),
      )

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector
        .produceCustomF[Suspend2[Nothing, _]](plan).use {
          Suspend2(_)
        }.unsafeRun()

      val s3Component = context.get[S3Component]
      val s3Client = context.get[S3Client]

      assert(s3Component eq s3Client.c)
      assert(s3Client eq s3Component.s)

      val startOps = context.get[Ref[Fn, Queue[Ops]]].get.unsafeRun().take(2)
      assert(startOps.toSet == Set(ComponentStart, ClientStart))

      val expectStopOps = startOps.reverse.map(_.invert)
      assert(context.get[Ref[Fn, Queue[Ops]]].get.unsafeRun().slice(2, 4) == expectStopOps)
    }

  }

}
