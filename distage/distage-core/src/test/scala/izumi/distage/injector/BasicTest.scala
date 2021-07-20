package izumi.distage.injector

import distage._
import izumi.distage.fixtures.BasicCases._
import izumi.distage.fixtures.SetCases._
import izumi.distage.model.PlannerInput
import izumi.distage.model.definition.Binding.SetElementBinding
import izumi.distage.model.definition.BindingTag
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.model.definition.conflicts.ConflictResolutionError
import izumi.distage.model.exceptions.{BadMutatorAxis, ConflictResolutionException, ProvisioningException}
import izumi.distage.model.plan.ExecutableOp.ImportDependency
import izumi.fundamentals.platform.functional.Identity
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

class BasicTest extends AnyWordSpec with MkInjector {

  "maintain correct operation order" in {
    import BasicCase1._
    val definition = PlannerInput(
      new ModuleDef {
        make[TestClass]
        make[TestDependency3]
        make[TestDependency0].from[TestImpl0]
        make[TestDependency1]
        make[TestCaseClass]
        make[LocatorDependent]
        make[TestInstanceBinding].from(TestInstanceBinding())
      },
      Activation.empty,
      DIKey[TestDependency1],
      DIKey[LocatorDependent],
    )

    val injector = mkInjector()
    val plan = injector.plan(definition)
    assert(plan.steps.exists(_.isInstanceOf[ImportDependency]))

    val exc = intercept[ProvisioningException] {
      injector.produce(plan).unsafeGet()
    }

    assert(exc.getMessage.linesIterator.toList.head == "Provisioner failed on 1 of 4 required operations, just 1 succeeded:")

//    val fixedPlan = plan.resolveImports {
//      case i if i.target == DIKey.get[NotInContext] => new NotInContext {}
//    }
//    val locator = injector.produce(fixedPlan).unsafeGet()
//    assert(locator.get[LocatorDependent].ref.get == locator)
  }

  "correctly handle empty typed sets" in {
    import SetCase1._

    val definition = PlannerInput.everything(new ModuleDef {
      make[TypedService[Int]].from[ServiceWithTypedSet]
      many[ExampleTypedCaseClass[Int]]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val s = context.get[TypedService[Int]]
    val ss = context.get[Set[ExampleTypedCaseClass[Int]]]
    assert(s.isInstanceOf[TypedService[Int]])
    assert(ss.isEmpty)
  }

  "provide LocatorRef during initialization" in {
    import BasicCase1._

    val definition = PlannerInput.everything(new ModuleDef {
      make[TestClass0]
      make[TestClass2].from {
        (ref: LocatorRef, test: TestClass0) =>
          assert(ref.unsafeUnstableMutableLocator().instances.nonEmpty)
          assert(test != null)
          assert(ref.unsafeUnstableMutableLocator().get[TestClass0] eq test)
          TestClass2(test)
      }
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val t = context.get[TestClass2]
    val r = context.get[LocatorRef]
    assert(t eq r.get.get[TestClass2])
  }

  "fails on wrong @Id annotation at compile-time" in {
    val res = intercept[TestFailedException] {
      assertCompiles("""
        import BadAnnotationsCase._

        val definition = PlannerInput.noGc(new ModuleDef {
          make[TestDependency0]
          make[TestClass]
        })

        val injector = mkInjector()
        injector.produce(injector.plan(definition)).unsafeGet().get[TestClass]
        """)
    }
    assert(res.getMessage.contains("BadIdAnnotationException"))
  }

  "regression test: issue #762 example (Predef.String vs. java.lang.String)" in {
    import BasicCaseIssue762._

    val definition = PlannerInput.everything(MyClassModule ++ ConfigModule)

    val injector = mkInjector()

    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[MyClass].a eq context.get[String]("a"))
    assert(context.get[MyClass].b eq context.get[String]("b"))
  }

  "support multiple bindings" in {
    import BasicCase1._
    val definition = PlannerInput.everything(new ModuleDef {
      many[JustTrait].named("named.empty.set")

      many[JustTrait]
        .add[JustTrait]
        .add(new Impl1)

      many[JustTrait]
        .named("named.set")
        .add(new Impl2())

      many[JustTrait]
        .named("named.set")
        .add[Impl3]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[Set[JustTrait]].size == 2)
    assert(context.get[Set[JustTrait]]("named.empty.set").isEmpty)
    assert(context.get[Set[JustTrait]]("named.set").size == 2)
  }

  "support nested multiple bindings" in {
    // https://github.com/7mind/izumi/issues/261
    import BasicCase1._
    val definition = PlannerInput.everything(new ModuleDef {
      many[JustTrait]
        .add(new Impl1)
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    val sub = Injector.inherit[Identity](context)
    val subplan = sub.plan(definition)
    val subcontext = injector.produce(subplan).unsafeGet()

    assert(context.get[Set[JustTrait]].size == 1)
    assert(subcontext.get[Set[JustTrait]].size == 1)
  }

  "support named bindings" in {
    import BasicCase2._
    val definition = PlannerInput.everything(new ModuleDef {
      make[TestClass]
        .named("named.test.class")
      make[TestDependency0].from[TestImpl0Bad]
      make[TestDependency0]
        .named("named.test.dependency.0")
        .from[TestImpl0Good]
      make[TestInstanceBinding]
        .named("named.test")
        .from(TestInstanceBinding())
      // test SetIdFromImplName
      make[TestDependency0].namedByImpl
        .from[TestImpl0Good]
      make[TestDependency0]
        .from[TestImpl0Bad]
        .namedByImpl
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[TestClass]("named.test.class").correctWired())
  }

  "fail on unsolvable conflicts" in {
    import BasicCase3._

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dependency].from[Impl1]
      make[Dependency].from[Impl2]
    })

    val injector = mkInjector()
    val exc = intercept[ConflictResolutionException] {
      injector.plan(definition)
    }
    assert(exc.conflicts.size == 1)
    assert(
      exc.conflicts.exists(
        e =>
          e.isInstanceOf[ConflictResolutionError.ConflictingDefs[?, ?]] &&
          e.asInstanceOf[ConflictResolutionError.ConflictingDefs[DIKey, Nothing]].defs.keySet.exists(_.key == DIKey.get[Dependency])
      )
    )
  }

  // BasicProvisionerTest
  "instantiate simple class" in {
    import BasicCase1._
    val definition = PlannerInput.everything(new ModuleDef {
      make[TestCaseClass2]
      make[TestInstanceBinding].from(new TestInstanceBinding)
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()
    val instantiated = context.get[TestCaseClass2]

    assert(instantiated.a.z.nonEmpty)
  }

  "handle set bindings" in {
    import SetCase1._

    val definition = PlannerInput.everything(new ModuleDef {
      make[Service2]
      make[Service0]
      make[Service1]
      make[Service3]

      many[SetTrait]
        .add[SetImpl1]
        .add[SetImpl2]
        .add[SetImpl3]

      many[SetTrait]
        .named("n1")
        .add[SetImpl1]
        .add[SetImpl2]
        .add[SetImpl3]

      many[SetTrait]
        .named("n2")
        .add[SetImpl1]
        .add[SetImpl2]
        .add[SetImpl3]

      many[SetTrait]
        .named("n3")
        .add[SetImpl1]
        .add[SetImpl2]
        .add[SetImpl3]
    })

    val injector = mkInjector()
    val plan = injector.plan(definition)

    val context = injector.produce(plan).unsafeGet()

    assert(context.get[Service0].set.size == 3)
    assert(context.get[Service1].set.size == 3)
    assert(context.get[Service2].set.size == 3)
    assert(context.get[Service3].set.size == 3)
  }

//  "support Plan.providerImport and Plan.resolveImport" in {
//    import BasicCase1._
//
//    val definition = PlannerInput.everything(new ModuleDef {
//      make[TestCaseClass2]
//    })
//
//    val injector = mkInjector()
//
//    val plan1 = injector.plan(definition)
//    val plan2 = injector.finish(plan1.toSemi.providerImport {
//      verse: String @Id("verse") =>
//        TestInstanceBinding(verse)
//    })
//    val plan3 = plan2.resolveImport[String](id = "verse") {
//      """ God only knows what I might do, god only knows what I might do, I don't fuck with god, I'm my own through
//        | Take two of these feel like Goku""".stripMargin
//    }
//
//    val context = injector.produce(plan3).unsafeGet()
//
//    assert(context.get[TestCaseClass2].a.z == context.get[String]("verse"))
//  }

  "preserve type annotations" in {
    import BasicCase4._

    val definition = PlannerInput.everything(new ModuleDef {
      make[Dependency].named("special")
      make[TestClass]
    })

    val injector = mkInjector()

    val plan = injector.plan(definition)
    val context = injector.produce(plan).unsafeGet()

    assert(context.get[TestClass] != null)
  }

  "handle set inclusions" in {
    val definition = PlannerInput.everything(new ModuleDef {
      make[Set[Int]].named("x").from(Set(1, 2, 3))
      make[Set[Int]].named("y").from(Set(4, 5, 6))
      many[Int].refSet[Set[Int]]("x")
      many[Int].refSet[Set[Int]]("y")

      make[Set[None.type]].from(Set(None))
      make[Set[Some[Int]]].from(Set(Some(7)))
      many[Option[Int]].refSet[Set[None.type]]
      many[Option[Int]].refSet[Set[Some[Int]]]
    })

    val context = mkInjector().produce(definition).unsafeGet()

    assert(context.get[Set[Int]] == Set(1, 2, 3, 4, 5, 6))
    assert(context.get[Set[Option[Int]]] == Set(None, Some(7)))
  }

  "handle multiple set element binds" in {
    val definition = PlannerInput.everything(new ModuleDef {
      make[Int].from(7)

      many[Int].add(0)
      many[Int].addSet(Set(1, 2, 3))
      many[Int].add(5)

      many[Int].add {
        i: Int => i - 1
      } // 6
      many[Int].addSet { // 7, 8, 9
        i: Int =>
          Set(i, i + 1, i + 2)
      }
    })

    val context = mkInjector().produce(definition).unsafeGet()
    assert(context.get[Set[Int]].toList.sorted == List(0, 1, 2, 3, 5, 6, 7, 8, 9))
  }

  "support empty sets" in {
    import BasicCase5._
    val definition = PlannerInput.everything(new ModuleDef {
      many[TestDependency]
      make[TestImpl1]
    })

    val context = mkInjector().produce(definition).unsafeGet()

    assert(context.get[TestImpl1].justASet == Set.empty)
  }

  "preserve tags in multi set bindings" in {
    import izumi.distage.dsl.TestTagOps._
    val definition = PlannerInput.everything(new ModuleDef {
      many[Int]
        .named("zzz")
        .add(5).tagged("t3")
        .addSet(Set(1, 2, 3)).tagged("t1", "t2")
        .addSet(Set(1, 2, 3)).tagged("t3", "t4")
    })

    assert(
      definition.bindings.bindings.collectFirst {
        case SetElementBinding(_, _, s, _) if Set.apply[BindingTag]("t1", "t2").diff(s).isEmpty => true
      }.nonEmpty
    )
  }

  "Can abstract over Id annotations with type aliases" in {
    import BasicCase7._

    val definition = PlannerInput.everything(new ModuleDef {
      make[Int].named("port").from(80)
      make[String].named("address").from("localhost")
      make[ServerConfig].from(ServerConfig)
    })

    val context = mkInjector().produce(definition).unsafeGet()

    assert(context.get[ServerConfig].port == context.get[Int]("port"))
    assert(context.get[ServerConfig].address == context.get[String]("address"))
  }

  "support mutations" in {
    import Mutations01._

    val definition = PlannerInput.everything(new ModuleDef {
      make[SomethingUseful].fromValue(SomethingUseful("x"))
      make[Mutable].fromValue(Mutable(1, None))
      modify[Mutable].by {
        _.flatAp {
          (u: SomethingUseful) => (m: Mutable) =>
            m.copy(b = Some(u))
        }
      }
    })

    println(mkInjector().plan(definition).render())
    val context = mkInjector().produce(definition).unsafeGet()

    assert(context.get[Mutable] == Mutable(1, Some(SomethingUseful("x"))))
  }

  "support mutations with axis tags when axis is configured" in {
    import Mutations01._

    val definition = PlannerInput.everything(
      new ModuleDef {
        make[SomethingUseful].fromValue(SomethingUseful("x"))

        make[Mutable].named("x").fromValue(Mutable(1, None))

        modify[Mutable].named("x").by {
          _.flatAp {
            (u: SomethingUseful) => (m: Mutable) =>
              m.copy(b = Some(u))
          }
        }

        modify[Mutable]
          .named("x") {
            (m: Mutable) =>
              m.copy(a = m.a + 10)
          }.tagged(Repo.Prod)

        modify[Mutable]
          .named("x") {
            (m: Mutable) =>
              m.copy(a = m.a + 20)
          }.tagged(Repo.Dummy)
      },
      Activation(Repo -> Repo.Prod),
    )

    val context = mkInjector().produce(definition).unsafeGet()

    assert(context.get[Mutable]("x") == Mutable(11, Some(SomethingUseful("x"))))
  }

  "support mutations with axis tags when axis is unconfigured" in {
    import Mutations01._

    val definition = PlannerInput.everything(
      new ModuleDef {
        make[SomethingUseful].fromValue(SomethingUseful("x"))

        make[Mutable].fromValue(Mutable(1, None))

        modify[Mutable].by {
          _.flatAp {
            (u: SomethingUseful) => (m: Mutable) =>
              m.copy(b = Some(u))
          }
        }

        modify[Mutable]
          .by {
            _.map {
              (m: Mutable) =>
                m.copy(a = m.a + 10)
            }
          }.tagged(Repo.Prod)

        modify[Mutable]
          .by {
            _.map {
              (m: Mutable) =>
                m.copy(a = m.a + 20)
            }
          }.tagged(Repo.Dummy)
      },
      Activation.empty,
    )

    intercept[BadMutatorAxis] {
      mkInjector().produce(definition).unsafeGet()
    }
  }

  "regression test: imports correctly specify which binding they are required by when missing" in {
    val definition = new ModuleDef {
      make[String].from((_: Int).toString)
    }

    val error = intercept[Throwable](mkInjector().produceGet[String](definition).unsafeGet())
    assert(error.getMessage.contains("String"))
  }

}
