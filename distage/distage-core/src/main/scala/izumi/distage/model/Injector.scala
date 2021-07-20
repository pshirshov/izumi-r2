package izumi.distage.model

import izumi.distage.model.definition.Axis.AxisChoice
import izumi.distage.model.definition.{Activation, Identifier, Lifecycle, ModuleBase}
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.plan.{DIPlan, Roots}
import izumi.distage.model.planning.PlanSplittingOps
import izumi.distage.model.providers.Functoid
import izumi.distage.model.provisioning.PlanInterpreter.FailedProvision
import izumi.distage.model.reflection.DIKey
import izumi.distage.planning.solver.PlanVerifier
import izumi.distage.planning.solver.PlanVerifier.PlanVerifierResult
import izumi.fundamentals.collections.nonempty.NonEmptySet
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.{Tag, TagK}

/**
  * Injector creates object graphs ([[izumi.distage.model.Locator]]s) from a [[izumi.distage.model.definition.ModuleDef]] or from an [[izumi.distage.model.plan.DIPlan]]
  *
  * @see [[izumi.distage.model.Planner]]
  * @see [[izumi.distage.model.Producer]]
  */
trait Injector[F[_]] extends Planner with Producer {
  @deprecated("should be removed with OrderedPlan", "13/04/2021")
  def ops: PlanSplittingOps
  /**
    * Create an an object graph described by the `input` module,
    * designate all arguments of the provided function as roots of the graph,
    * and run the function, deallocating the object graph when the function exits.
    *
    * {{{
    *   class Hello { def hello() = println("hello") }
    *   class World { def world() = println("world") }
    *
    *   Injector()
    *     .produceRun(new ModuleDef {
    *       make[Hello]
    *       make[World]
    *     }) {
    *       (hello: Hello, world: World) =>
    *         hello.hello()
    *         world.world()
    *     }
    * }}}
    *
    * This is useful for the common case when you want to run an effect using the produced objects from the object graph
    * and deallocate the object graph once the effect is finished
    *
    * `Injector[F]().produceRun[A](moduleDef)(fn)` is a short-hand for:
    *
    * {{{
    *   Injector[F]()
    *     .produce(moduleDef, Roots(fn.get.diKeys.toSet))
    *     .use(_.run(fn)): F[A]
    * }}}
    *
    * @param bindings   Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    * @param activation A map of axes of configuration to choices along these axes
    * @param function   N-ary [[izumi.distage.model.providers.Functoid]] function for which arguments will be designated as roots and provided from the object graph
    */
  final def produceRun[A](
    bindings: ModuleBase,
    activation: Activation = Activation.empty,
  )(function: Functoid[F[A]]
  ): F[A] = {
    produceCustomF(plan(PlannerInput(bindings, activation, function.get.diKeys.toSet))).use(_.run(function))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by the `input` module,
    * designate all arguments of the provided function as roots of the graph
    * and run the function.
    *
    * {{{
    *   class Hello { def hello() = println("hello") }
    *   class World { def world() = println("world") }
    *
    *   Injector()
    *     .produceEval(new ModuleDef {
    *       make[Hello]
    *       make[World]
    *     }) {
    *       (hello: Hello, world: World) =>
    *         hello.hello()
    *         world
    *     }
    *     .use {
    *       world =>
    *         world.world()
    *     }
    * }}}
    *
    * This is useful for the common case when you want to run an effect using the produced objects from the object graph,
    * without finalizing the object graph yet
    *
    * `Injector[F]().produceEval[A](moduleDef)(fn)` is a short-hand for:
    *
    * {{{
    *   Injector[F]()
    *     .produce(moduleDef, Roots(fn.get.diKeys.toSet))
    *     .evalMap(_.run(fn)): Lifecycle[F, A]
    * }}}
    *
    * @param bindings   Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    * @param activation A map of axes of configuration to choices along these axes
    * @param function   N-ary [[izumi.distage.model.providers.Functoid]] function for which arguments will be designated as roots and provided from the object graph
    */
  final def produceEval[A](
    bindings: ModuleBase,
    activation: Activation = Activation.empty,
  )(function: Functoid[F[A]]
  ): Lifecycle[F, A] = {
    produceCustomF(plan(PlannerInput(bindings, activation, function.get.diKeys.toSet))).evalMap(_.run(function))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by the `input` module,
    * designate `A` as the root of the graph and retrieve `A` from the result.
    *
    * {{{
    *   class HelloWorld {
    *     def hello() = println("hello world")
    *   }
    *
    *   Injector()
    *     .produceGet[HelloWorld](new ModuleDef {
    *       make[HelloWorld]
    *     })
    *     .use(_.hello())
    * }}}
    *
    * This is useful for the common case when your main logic class
    * is the root of your graph AND the object you want to use immediately.
    *
    * `Injector[F]().produceGet[A](moduleDef)` is a short-hand for:
    *
    * {{{
    *   Injector[F]()
    *     .produce(moduleDef, Roots(DIKey.get[A]))
    *     .map(_.get[A]): Lifecycle[F, A]
    * }}}
    *
    * @param bindings   Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    * @param activation A map of axes of configuration to choices along these axes
    */
  final def produceGet[A: Tag](bindings: ModuleBase, activation: Activation): Lifecycle[F, A] = {
    produceCustomF(plan(PlannerInput(bindings, activation, DIKey.get[A]))).map(_.get[A])
  }
  final def produceGet[A: Tag](bindings: ModuleBase): Lifecycle[F, A] = {
    produceGet[A](bindings, Activation.empty)
  }
  final def produceGet[A: Tag](name: Identifier)(bindings: ModuleBase, activation: Activation = Activation.empty): Lifecycle[F, A] = {
    produceCustomF(plan(PlannerInput(bindings, activation, DIKey.get[A].named(name)))).map(_.get[A](name))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by `input`
    *
    * {{{
    *   class HelloWorld {
    *     def hello() = println("hello world")
    *   }
    *
    *   Injector()
    *     .produce(PlannerInput(
    *       bindings = new ModuleDef {
    *         make[HelloWorld]
    *       },
    *       activation = Activation.empty,
    *       roots = Roots.target[HelloWorld],
    *     ))
    *     .use(_.get[HelloWorld].hello())
    * }}}
    *
    * @param input Bindings created by [[izumi.distage.model.definition.ModuleDef]] DSL
    *              and garbage collection roots.
    *
    *              Garbage collector will remove all bindings that aren't direct or indirect dependencies
    *              of the chosen `root` DIKeys from the plan - they will never be instantiated.
    *
    *              If left empty, garbage collection will not be performed – that would be equivalent to
    *              designating all DIKeys as roots.
    * @return A Resource value that encapsulates allocation and cleanup of the object graph described by `input`
    */
  final def produce(input: PlannerInput): Lifecycle[F, Locator] = {
    produceCustomF[F](plan(input))
  }
  final def produce(bindings: ModuleBase, roots: Roots, activation: Activation = Activation.empty): Lifecycle[F, Locator] = {
    produceCustomF[F](plan(PlannerInput(bindings, activation, roots)))
  }

  /**
    * Create an effectful [[izumi.distage.model.definition.Lifecycle]] value that encapsulates the
    * allocation and cleanup of an object graph described by an existing `plan`
    *
    * {{{
    *   class HelloWorld {
    *     def hello() = println("hello world")
    *   }
    *
    *   val injector = Injector()
    *
    *   val plan = injector.plan(PlannerInput(
    *       bindings = new ModuleDef {
    *         make[HelloWorld]
    *       },
    *       activation = Activation.empty,
    *       roots = Roots.target[HelloWorld],
    *     ))
    *
    *   injector
    *     .produce(plan)
    *     .use(_.get[HelloWorld].hello())
    * }}}
    *
    * @param plan Computed wiring plan, may be produced by calling the [[plan]] method
    *
    * @return A Resource value that encapsulates allocation and cleanup of the object graph described by `input`
    */
  final def produce(plan: DIPlan): Lifecycle[F, Locator] = {
    produceCustomF[F](plan)
  }

  /** Produce [[izumi.distage.model.Locator]] interpreting effect and resource bindings into the provided effect type */
  final def produceCustomF[G[_]: TagK: QuasiIO](plannerInput: PlannerInput): Lifecycle[G, Locator] = {
    produceCustomF[G](plan(plannerInput))
  }
  final def produceDetailedCustomF[G[_]: TagK: QuasiIO](plannerInput: PlannerInput): Lifecycle[G, Either[FailedProvision[G], Locator]] = {
    produceDetailedCustomF[G](plan(plannerInput))
  }

  /** Produce [[izumi.distage.model.Locator]], supporting only effect and resource bindings in `Identity` */
  final def produceCustomIdentity(plannerInput: PlannerInput): Lifecycle[Identity, Locator] = {
    produceCustomF[Identity](plan(plannerInput))
  }
  final def produceDetailedIdentity(plannerInput: PlannerInput): Lifecycle[Identity, Either[FailedProvision[Identity], Locator]] = {
    produceDetailedCustomF[Identity](plan(plannerInput))
  }

  @deprecated("Use .produceRun. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceRunF[A: Tag](bindings: ModuleBase, activation: Activation = Activation.empty)(function: Functoid[F[A]]): F[A] =
    produceRun[A](bindings, activation)(function)
  @deprecated("Use .produceEval. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceEvalF[A: Tag](bindings: ModuleBase, activation: Activation = Activation.empty)(function: Functoid[F[A]]): Lifecycle[F, A] =
    produceEval[A](bindings, activation)(function)

  @deprecated("Use .produceGet. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceGetF[A: Tag](bindings: ModuleBase, activation: Activation): Lifecycle[F, A] =
    produceGet[A](bindings, activation)
  @deprecated("Use .produceGet. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceGetF[A: Tag](bindings: ModuleBase): Lifecycle[F, A] =
    produceGet[A](bindings)
  @deprecated("Use .produceGet. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceGetF[A: Tag](name: Identifier)(bindings: ModuleBase, activation: Activation = Activation.empty): Lifecycle[F, A] =
    produceGet[A](name)(bindings, activation)

  @deprecated("Use .produce. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceF(input: PlannerInput): Lifecycle[F, Locator] =
    produce(input)
  @deprecated("Use .produce. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceF(bindings: ModuleBase, roots: Roots, activation: Activation = Activation.empty): Lifecycle[F, Locator] =
    produce(bindings, roots, activation)

  @deprecated("Use .produce. Parameterize Injector with `F` on creation: `Injector[F]()`", "1.0")
  final def produceF(plan: DIPlan): Lifecycle[F, Locator] = {
    produceCustomF[F](plan)
  }

  /**
    * Efficiently check all possible paths for the given module to the given `roots`,
    *
    * This is a "raw" version of [[izumi.distage.framework.PlanCheck]] API, please use `PlanCheck` for all non-exotic needs.
    *
    * This method executes at runtime, to check correctness at compile-time use `PlanCheck` API from `distage-framework` module.
    *
    * @see [[https://izumi.7mind.io/distage/distage-framework.html#compile-time-checks Compile-Time Checks]]
    *
    * @throws PlanCheckException on found issues
    */
  final def assert(
    bindings: ModuleBase,
    roots: Roots,
    excludedActivations: Set[NonEmptySet[AxisChoice]] = Set.empty,
  ): Unit = {
    PlanVerifier()
      .verify[F](
        bindings = bindings,
        roots = roots,
        providedKeys = providedKeys,
        excludedActivations = excludedActivations.map(_.map(_.toAxisPoint)),
      ).throwOnError()
  }

  /**
    * Efficiently check all possible paths for the given module to the given `roots`,
    *
    * This is a "raw" version of [[izumi.distage.framework.PlanCheck]] API, please use `PlanCheck` for all non-exotic needs.
    *
    * This method executes at runtime, to check correctness at compile-time use `PlanCheck` API from `distage-framework` module.
    *
    * @see [[https://izumi.7mind.io/distage/distage-framework.html#compile-time-checks Compile-Time Checks]]
    *
    * @return Set of issues if any.
    *         Does not throw.
    */
  final def verify(
    bindings: ModuleBase,
    roots: Roots,
    excludedActivations: Set[NonEmptySet[AxisChoice]] = Set.empty,
  ): PlanVerifierResult = {
    PlanVerifier()
      .verify[F](
        bindings = bindings,
        roots = roots,
        providedKeys = providedKeys,
        excludedActivations = excludedActivations.map(_.map(_.toAxisPoint)),
      )
  }

  /** Keys that will be available to the module interpreted by this Injector, includes parent Locator keys, [[izumi.distage.modules.DefaultModule]] & Injector's self-reference keys */
  def providedKeys: Set[DIKey]
  def providedEnvironment: InjectorProvidedEnv

  protected[this] implicit def tagK: TagK[F]
  protected[this] implicit def F: QuasiIO[F]
}
