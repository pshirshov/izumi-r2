# Overview

@@toc { depth=2 }

## Quick Start

```scala mdoc:reset:invisible:to-string
// ##### New overview
// (sentence-two per concept + link to details [or inline expand? or these should just be chapters?])
//
// ###### Concepts
// ####### ModuleDef
// ######## Activation axis
// ######## Functoid
// ####### Injector
// ######## Object graph
// ######## Planning
// ######## Verification
// ######### Roots
// ####### Lifecycle
// ####### Roles
// ####### Plugins
// ####### Compile-time verification
// ####### Testing with injected components
//
// ##### Binding Types
// ###### Singleton Bindings
// ###### Named bindings
// ###### Set bindings
// ####### Weak Set bindings
// ###### Lifecycle / Resource bindings
// ###### Effect bindings
// ###### ZIO Environment / Reader bindings
// ###### Config bindings
// ###### Role bindings
// ###### Docker bindings
//
// ##### Auto-generated things
// ###### Effect support modules
// ###### Auto-Traits
// ###### Auto-Factories
// ###### Class constructors
// ###### Circular dependency resolution
```

### Dependencies

Add the `distage-core` library:

@@dependency[sbt] {
  group="io.7mind.izumi"
  artifact="distage-core_2.13"
  version="$izumi.version$"
}


### Hello World example

Suppose we have an abstract `Greeter` component, and some other components that depend on it:

```scala mdoc:reset:invisible:to-string
var counter = 0
val names = Array("izumi", "kai", "Pavel")
def HACK_OVERRIDE_getStrLn = zio.ZIO {
  val n = names(counter % names.length)
  counter += 1
  println(s"> $n")
  n
}
```

```scala mdoc:override:to-string
import zio.RIO
import zio.console.{Console, getStrLn, putStrLn}

trait Greeter {
  def hello(name: String): RIO[Console, Unit]
}

final class PrintGreeter extends Greeter {
  override def hello(name: String) =
    putStrLn(s"Hello $name!")
}

trait Byer {
  def bye(name: String): RIO[Console, Unit]
}

final class PrintByer extends Byer {
  override def bye(name: String) =
    putStrLn(s"Bye $name!")
}

final class HelloByeApp(
  greeter: Greeter,
  byer: Byer,
) {
  def run: RIO[Console, Unit] = {
    for {
      _    <- putStrLn("What's your name?")
      name <- HACK_OVERRIDE_getStrLn
      _    <- greeter.hello(name)
      _    <- byer.bye(name)
    } yield ()
  }
}
```

To actually run the `HelloByeApp`, we have to wire implementations of `Greeter` and `Byer` into it.
We will not do it directly. First we'll only declare the component interfaces we have, and the implementations we want for them:

```scala mdoc:to-string
import distage.ModuleDef

def HelloByeModule = new ModuleDef {
  make[Greeter].from[PrintGreeter]
  make[Byer].from[PrintByer]
  make[HelloByeApp] // `.from` is not required for concrete classes
}
```

`ModuleDef` merely contains a description of the desired object graph, let's transform that high-level description into an
actionable series of steps - an @scaladoc[DIPlan](izumi.distage.model.plan.DIPlan), a datatype we can
@ref[inspect](debugging.md#pretty-printing-plans), @ref[test](debugging.md#testing-plans) or @ref[verify at compile-time](distage-framework.md#compile-time-checks) – without having to actually create objects or execute effects.

```scala mdoc:to-string
import distage.{Activation, Injector, Roots}

val injector = Injector[RIO[Console, _]]()

val plan = injector.plan(HelloByeModule, Activation.empty, Roots.target[HelloByeApp])
```

The series of steps must be executed to produce the object graph.

`Injector.produce` will interpret the steps into a @ref[`Lifecycle`](basics.md#resource-bindings-lifecycle) value holding the lifecycle of the object graph:

```scala mdoc:to-string
import zio.Runtime.default.unsafeRun

// Interpret into a Lifecycle value

val resource = injector.produce(plan)

// Use the object graph:
// After `.use` exits, all objects will be deallocated,
// and all allocated resources will be freed.

val effect = resource.use {
  objects =>
    objects.get[HelloByeApp].run
}

// Run the resulting program

unsafeRun(effect)
```

### Singleton components

`distage` creates components at most once, even if multiple other objects depend on them.

A given component `X` will be the _same_ `X` everywhere in the object graph, i.e. a singleton.

It's impossible to create non-singletons in `distage`.

### Named components

If you need multiple singleton instances of the same type, you may create "named" instances and disambiguate between them using @scaladoc[`@distage.Id`](izumi.distage.model.definition.Id) annotation. (`javax.inject.Named` is also supported)

```scala mdoc:silent
import distage.Id

def negateByer(otherByer: Byer): Byer = {
  new Byer {
    def bye(name: String) =
     otherByer.bye(s"NOT-$name")
  }
}

new ModuleDef {
  make[Byer].named("byer-1").from[PrintByer]
  make[Byer].named("byer-2").from {
    otherByer: Byer @Id("byer-1") =>
      negateByer(otherByer)
  }
}
```


You can use `make[_].annotateParameter` method instead of an annotation, to attach a name component to an existing constructor:

```scala mdoc:silent
new ModuleDef {
  // same binding as above
  make[Byer].named("byer-2")
    .from(negateByer(_))
    .annotateParameter[Byer]("byer-1")
}
```

You can also abstract over annotations using type aliases and/or string constants (`final val`):

```scala mdoc:to-string
object Ids {
  final val byer1Id = "byer-1"
  type Byer1 = Byer @Id(byer1Id)
}
```

### Non-singleton components

You cannot embed non-singletons into the object graph, but you may create them as normal using factories. `distage`'s @ref[Auto-Factories](#auto-factories) can generate implementations for your factories, removing the associated boilerplate.

While Auto-Factories may remove the boilerplate of generating factories for singular components, if you need to create a new non-trivial subgraph dynamically, you'll need to run `Injector` again – you may use `Injector.inherit` to reuse components from the outer object graph in your new nested object graph, see @ref[Injector Inheritance](advanced-features.md#injector-inheritance). It's safe, performance-wise, to run `Injector` to create nested graphs, it's extremely fast.

## Real-world example

Check out [`distage-example`](https://github.com/7mind/distage-example) sample project for a complete example built using `distage`, @ref[bifunctor tagless final](../bio/00_bio.md), `http4s`, `doobie` and `zio` libraries.

It shows how to write an idiomatic `distage`-style from scratch and how to:

- write tests using @ref[`distage-testkit`](distage-testkit.md)
- setup portable test environments using @ref[`distage-framework-docker`](distage-framework-docker.md)
- create @ref[role-based applications](distage-framework.md#roles)
- enable @ref[compile-time checks](distage-framework.md#compile-time-checks) for fast feedback on wiring errors

```scala mdoc:invisible
/**
add to distage-example

- how to setup graalvm native image with distage
- how to debug dump graphs and render to graphviz [Actually, we have a GUI component now, can we show em there???]
*/
```

## Activation Axis

You can choose between different implementations of a component using "Activation axis":

```scala mdoc:to-string
import distage.{Axis, Activation, ModuleDef, Injector}

class AllCapsGreeter extends Greeter {
  def hello(name: String) =
    putStrLn(s"HELLO ${name.toUpperCase}")
}

// declare a configuration axis for our components

object Style extends Axis {
  case object AllCaps extends AxisChoiceDef
  case object Normal extends AxisChoiceDef
}

// Declare a module with several implementations of Greeter
// but in different environments

def TwoImplsModule = new ModuleDef {
  make[Greeter].tagged(Style.Normal)
    .from[PrintGreeter]

  make[Greeter].tagged(Style.AllCaps)
    .from[AllCapsGreeter]
}

// Combine previous `HelloByeModule` with our new module
// While overriding `make[Greeter]` bindings from the first module

def CombinedModule = HelloByeModule overriddenBy TwoImplsModule

// Choose component configuration when making an Injector:

unsafeRun {
  Injector()
    .produceGet[HelloByeApp](CombinedModule, Activation(Style -> Style.AllCaps))
    .use(_.run)
}

// Check that result changes with a different configuration:

unsafeRun {
  Injector()
    .produceGet[HelloByeApp](CombinedModule, Activation(Style -> Style.Normal))
    .use(_.run)
}
```

@scaladoc[distage.StandardAxis](izumi.distage.model.definition.StandardAxis$) contains bundled Axes for back-end development:

- @scaladoc[Repo](izumi.distage.model.definition.StandardAxis$$Repo$) axis, with `Prod`/`Dummy` choices, describes any entities which may store and persist state or "repositories". e.g. databases, message queues, KV storages, file systems, etc. Those may typically have both in-memory `Dummy` implementations and heavyweight `Prod` implementations using external databases.

- @scaladoc[Mode](izumi.distage.model.definition.StandardAxis$$Mode$) axis, with `Prod`/`Test` choices, describes a generic choice between production and test implementations of a component.

- @scaladoc[World](izumi.distage.model.definition.StandardAxis$$World$) axis, with `Real`/`Mock` choices, describes third-party integrations which are not controlled by the application and provided "as is". e.g. Facebook API, Google API, etc. those may contact a `Real` external integration or a `Mock` one with predefined responses.

- @scaladoc[Scene](izumi.distage.model.definition.StandardAxis$$Scene$) axis with `Managed`/`Provided` choices, describes whether external services required by the application should be set-up on the fly by an orchestrator library such as @ref[`distage-framework-docker`](distage-framework-docker.md) (`Scene.Managed`), or whether the application should try to connect to external services as if they already exist in the environment (`Scene.Provided`).
  We call a set of external services required by the application a `Scene`, etymology being that the running external services required by the application are like a "scene" that the "staff" (the orchestrator) must prepare for the "actor" (the application) to enter.

In `distage-framework`'s @scaladoc[RoleAppMain](izumi.distage.roles.RoleAppMain), you can choose axes using the `-u` command-line parameter:

```
./launcher -u repo:dummy -u env:prod app1
```

In `distage-testkit`, choose axes using @scaladoc[TestConfig](izumi.distage.testkit.TestConfig):

```scala mdoc:to-string
import distage.StandardAxis.Repo
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.Spec2

class AxisTest extends Spec2[zio.IO] {

  // choose implementations `.tagged` as `Repo.Dummy` over those tagged `Repo.Prod`
  override def config: TestConfig = super.config.copy(
    activation = Activation(Repo -> Repo.Dummy)
  )

}
```

### Multi-dimensionality

There may be many configuration axes in an application and components can specify multiple axis choices at once:

```scala mdoc:to-string
import distage.StandardAxis.Mode

class TestPrintGreeter extends Greeter {
  def hello(name: String) =
    putStrLn(s"Test 1 2, hello $name")
}

// declare 3 possible implementations

def TestModule = new ModuleDef {
  make[Greeter].tagged(Style.Normal, Mode.Prod).from[PrintGreeter]
  make[Greeter].tagged(Style.Normal, Mode.Test).from[TestPrintGreeter]
  make[Greeter].tagged(Style.AllCaps).from[AllCapsGreeter]
}

def runWith(activation: Activation) = unsafeRun {
  Injector().produceRun(TestModule, activation) {
    greeter: Greeter => greeter.hello("$USERNAME")
  }
}

// Production Normal Greeter

runWith(Activation(Style -> Style.Normal, Mode -> Mode.Prod))

// Test Normal Greeter

runWith(Activation(Style -> Style.Normal, Mode -> Mode.Test))

// Both Production and Test Caps Greeters are the same:

runWith(Activation(Style -> Style.AllCaps, Mode -> Mode.Prod))

runWith(Activation(Style -> Style.AllCaps, Mode -> Mode.Test))
```

#### Specificity and defaults

When multiple dimensions are attached to a binding, bindings with less specified dimensions will be considered less specific
and will be overridden by bindings with more dimensions, if all of those dimensions are explicitly set.

A binding with no attached dimensions is considered a "default" vs. a binding with attached dimensions. A default will be chosen only if all other bindings are explicitly contradicted by passed activations. If the dimensions for other bindings are merely unset, it will cause an ambiguity error.

Example of these rules:

```scala mdoc:to-string
import scala.util.Try

sealed trait Color
case object RED extends Color
case object Blue extends Color
case object Green extends Color

// Defaults:

def DefaultsModule = new ModuleDef {
  make[Color].from(Green)
  make[Color].tagged(Style.AllCaps).from(RED)
}

Injector().produceRun(DefaultsModule, Activation(Style -> Style.AllCaps))(println(_: Color))

Injector().produceRun(DefaultsModule, Activation(Style -> Style.Normal))(println(_: Color))

// ERROR Ambiguous without Style
Try { Injector().produceRun(DefaultsModule, Activation.empty)(println(_: Color)) }.isFailure

// Specificity

def SpecificityModule = new ModuleDef {
  make[Color].tagged(Mode.Test).from(Blue)
  make[Color].tagged(Mode.Prod).from(Green)
  make[Color].tagged(Mode.Prod, Style.AllCaps).from(RED)
}

Injector().produceRun(SpecificityModule, Activation(Mode -> Mode.Prod, Style -> Style.AllCaps))(println(_: Color))

Injector().produceRun(SpecificityModule, Activation(Mode -> Mode.Test, Style -> Style.AllCaps))(println(_: Color))

Injector().produceRun(SpecificityModule, Activation(Mode -> Mode.Prod, Style -> Style.Normal))(println(_: Color))

Injector().produceRun(SpecificityModule, Activation(Mode -> Mode.Test))(println(_: Color))

// ERROR Ambiguous without Mode
Try { Injector().produceRun(SpecificityModule, Activation(Style -> Style.Normal))(println(_: Color)) }.isFailure
```

## Resource Bindings, Lifecycle

You can specify object lifecycle by injecting @scaladoc[distage.Lifecycle](izumi.distage.model.definition.Lifecycle), [cats.effect.Resource](https://typelevel.org/cats-effect/datatypes/resource.html) or
[zio.ZManaged](https://zio.dev/docs/datatypes/datatypes_managed)
values specifying the allocation and finalization actions of an object.

When ran, distage `Injector` itself returns a `Lifecycle` value that describes actions to create and finalize the object graph; the `Lifecycle` value is pure and can be reused multiple times.

A `Lifecycle` is executed using its `.use` method, the function passed to `use` will receive an allocated resource and when the function exits the resource will be deallocated. `Lifecycle` is generally not invalidated after `.use` and may be executed multiple times.

Example with `cats.effect.Resource`:

```scala mdoc:reset:to-string
import distage.{Roots, ModuleDef, Injector}
import cats.effect.{Resource, IO}

class DBConnection
class MessageQueueConnection

val dbResource = Resource.make(
  acquire = IO {
    println("Connecting to DB!")
    new DBConnection
})(release = _ => IO(println("Disconnecting DB")))

val mqResource = Resource.make(
  acquire = IO {
   println("Connecting to Message Queue!")
   new MessageQueueConnection
})(release = _ => IO(println("Disconnecting Message Queue")))

class MyApp(
  db: DBConnection,
  mq: MessageQueueConnection,
) {
  val run = {
    IO(println("Hello World!"))
  }
}

def module = new ModuleDef {
  make[DBConnection].fromResource(dbResource)
  make[MessageQueueConnection].fromResource(mqResource)
  make[MyApp]
}
```

Will produce the following output:

```scala mdoc:to-string
import distage.DIKey

val objectGraphResource = {
  Injector[IO]()
    .produce(module, Roots.target[MyApp])
}

objectGraphResource
  .use(_.get[MyApp].run)
  .unsafeRunSync()
```

Lifecycle management with `Lifecycle` is also available without an effect type, via `Lifecycle.Simple` and `Lifecycle.Mutable`:

```scala mdoc:reset:to-string
import distage.{Lifecycle, ModuleDef, Injector}

class Init {
  var initialized = false
}

class InitResource extends Lifecycle.Simple[Init] {
  override def acquire = {
    val init = new Init
    init.initialized = true
    init
  }
  override def release(init: Init) = {
    init.initialized = false
  }
}

def module = new ModuleDef {
  make[Init].fromResource[InitResource]
}

val closedInit = Injector()
  .produceGet[Init](module)
  .use {
    init =>
      println(init.initialized)
      init
}

println(closedInit.initialized)
```

`Lifecycle` forms a monad and has the expected `.map`, `.flatMap`, `.evalMap`, `.mapK` methods.

You can convert between a `Lifecycle` and `cats.effect.Resource` via `Lifecycle#toCats`/`Lifecycle.fromCats` methods,
and between a `Lifecycle` and `zio.ZManaged` via `Lifecycle#toZIO`/`Lifecycle.fromZIO` methods.

### Inheritance helpers

The following helpers allow defining `Lifecycle` sub-classes using expression-like syntax:

- @scaladoc[Lifecycle.Of](izumi.distage.model.definition.Lifecycle$$Of)
- @scaladoc[Lifecycle.OfInner](izumi.distage.model.definition.Lifecycle$$OfInner)
- @scaladoc[Lifecycle.OfCats](izumi.distage.model.definition.Lifecycle$$OfCats)
- @scaladoc[Lifecycle.OfZIO](izumi.distage.model.definition.Lifecycle$$OfZIO)
- @scaladoc[Lifecycle.LiftF](izumi.distage.model.definition.Lifecycle$$LiftF)
- @scaladoc[Lifecycle.Make](izumi.distage.model.definition.Lifecycle$$Make)
- @scaladoc[Lifecycle.Make_](izumi.distage.model.definition.Lifecycle$$Make_)
- @scaladoc[Lifecycle.MakePair](izumi.distage.model.definition.Lifecycle$$MakePair)
- @scaladoc[Lifecycle.FromAutoCloseable](izumi.distage.model.definition.Lifecycle$$FromAutoCloseable)
- @scaladoc[Lifecycle.SelfOf](izumi.distage.model.definition.Lifecycle$$SelfOf)
- @scaladoc[Lifecycle.MutableOf](izumi.distage.model.definition.Lifecycle$$MutableOf)

The main reason to employ them is to workaround a limitation in Scala 2's eta-expansion — when converting a method to a function value,
Scala always tries to fulfill implicit parameters eagerly instead of making them parameters of the function value,
this limitation makes it harder to inject implicits using `distage`.

However, when using `distage`'s type-based syntax: `make[A].fromResource[A.Resource[F]]` —
this limitation does not apply and implicits inject successfully.

So to workaround this limitation you can convert an expression based resource-constructor:

```scala mdoc:reset:to-string
import distage.Lifecycle, cats.Monad

class A(val n: Int)

object A {

  def resource[F[_]: Monad]: Lifecycle[F, A] =
    Lifecycle.pure[F](new A(1))

}
```

Into a class-based form:

```scala mdoc:reset:to-string
import distage.Lifecycle, cats.Monad

class A(val n: Int)

object A {

  final class Resource[F[_]: Monad]
    extends Lifecycle.Of[F, A](
      Lifecycle.pure[F](new A(1))
    )

}
```

And inject successfully using `make[A].fromResource[A.Resource[F]]` syntax of @scaladoc[ModuleDefDSL](izumi.distage.model.definition.dsl.ModuleDefDSL).

The following helpers ease defining `Lifecycle` sub-classes using traditional inheritance where `acquire`/`release` parts are defined as methods:

- @scaladoc[Lifecycle.Basic](izumi.distage.model.definition.Lifecycle$$Basic)
- @scaladoc[Lifecycle.Simple](izumi.distage.model.definition.Lifecycle$$Simple)
- @scaladoc[Lifecycle.Mutable](izumi.distage.model.definition.Lifecycle$$Mutable)
- @scaladoc[Lifecycle.MutableNoClose](izumi.distage.model.definition.Lifecycle$$MutableNoClose)
- @scaladoc[Lifecycle.Self](izumi.distage.model.definition.Lifecycle$$Self)
- @scaladoc[Lifecycle.SelfNoClose](izumi.distage.model.definition.Lifecycle$$SelfNoClose)
- @scaladoc[Lifecycle.NoClose](izumi.distage.model.definition.Lifecycle$$NoClose)

## Out-of-the-box typeclass instances

Typeclass instances for popular typeclass hierarchies are included by default for the effect type in which `distage` is running.

Whenever your effect type implements @ref[BIO](../bio/00_bio.md) or [cats-effect](https://typelevel.org/cats-effect/) typeclasses, their instances will be summonable without adding them into modules.
This applies for `ZIO`, `cats.effect.IO`, `monix`, `monix-bio` and any other effect type with relevant typeclass instances in implicit scope.

- For `ZIO`, `monix-bio` and any other implementors of @ref[BIO](../bio/00_bio.md) typeclasses, `BIO` hierarchy instances will be included.
- For `ZIO`, `cats-effect` instances will be included only if ZIO [`interop-cats`](https://github.com/zio/interop-cats/) library is on the classpath.

Example usage:

```scala mdoc:reset:to-string
import cats.effect.{IO, Sync}
import distage.{Activation, DefaultModule, Injector, Module, TagK}
import izumi.distage.model.effect.QuasiIO

def polymorphicHelloWorld[F[_]: TagK: QuasiIO: DefaultModule]: F[Unit] = {
  Injector[F]().produceRun(
    Module.empty, // we do not define _any_ components
    Activation.empty,
  ) {
      (F: Sync[F]) => // cats.effect.Sync[F] is available anyway
        F.delay(println("Hello world!"))
  }
}

val catsEffectHello = polymorphicHelloWorld[cats.effect.IO]

val monixHello = polymorphicHelloWorld[monix.eval.Task]

val zioHello = polymorphicHelloWorld[zio.IO[Throwable, _]]

val monixBioHello = polymorphicHelloWorld[monix.bio.IO[Throwable, _]]
```

See @scaladoc[`DefaultModule`](izumi.distage.modules.DefaultModule) implicit for implementation details. For details on
what exact components are available for each effect type, see
@scaladoc[ZIOSupportModule](izumi.distage.modules.support.ZIOSupportModule),
@scaladoc[CatsIOSupportModule](izumi.distage.modules.support.CatsIOSupportModule),
@scaladoc[MonixSupportModule](izumi.distage.modules.support.MonixSupportModule),
@scaladoc[MonixBIOSupportModule](izumi.distage.modules.support.MonixBIOSupportModule),
@scaladoc[ZIOCatsEffectInstancesModule](izumi.distage.modules.typeclass.ZIOCatsEffectInstancesModule), respectively.

DefaultModule occurs as an implicit parameter in `distage` entrypoints that require an effect type parameter, namely: `Injector[F]()` in `distage-core`, @ref[`extends RoleAppMain[F]`](distage-framework.md#roles) and @ref[`extends PlanCheck.Main[F]`](distage-framework.md#compile-time-checks) in `distage-framework` and @ref[`extends Spec1[F]`](distage-testkit.md) in `distage-testkit`.

## Set Bindings

Set bindings are useful for implementing listeners, plugins, hooks, http routes, healthchecks, migrations, etc.
Everywhere where a collection of components is required, a Set Binding is appropriate.

To define a Set binding use `.many` and `.add` methods of the @scaladoc[ModuleDef](izumi.distage.model.definition.ModuleDef) DSL.

As an example, we may declare multiple command handlers and use them to interpret user input in a REPL


```scala mdoc:reset:to-string
import distage.ModuleDef

final case class CommandHandler(
  handle: PartialFunction[String, String]
)

val additionHandler = CommandHandler {
  case s"$x + $y" => s"${x.toInt + y.toInt}"
}

object AdditionModule extends ModuleDef {
  many[CommandHandler]
    .add(additionHandler)
}
```

We've used `many` method to declare an open `Set` of command handlers and then added one handler to it.

When module definitions are combined, elements for the same type of `Set` will be merged together into a larger set.

You can summon a Set binding by summoning a scala `Set`, as in `Set[CommandHandler]`.

Let's define a new module with another handler:

```scala mdoc:to-string
val subtractionHandler = CommandHandler {
  case s"$x - $y" => s"${x.toInt - y.toInt}"
}

object SubtractionModule extends ModuleDef {
  many[CommandHandler]
    .add(subtractionHandler)
}
```

Let's create a command-line application using our command handlers:

```scala mdoc:invisible:to-string
implicit final class InjRun_NO_IDENTITY_TYPE_IN_OUTPUT(inj: distage.Injector[izumi.fundamentals.platform.functional.Identity]) {
  def HACK_OVERRIDE_produceRun[A](m: distage.Module)(p: distage.Functoid[A]): A = inj.produceRun[A](m)(p)
}
implicit final class LifecycleUnsafeGet_NO_IDENTITY_TYPE_IN_OUTPUT[A](lifecycle: distage.Lifecycle[izumi.fundamentals.platform.functional.Identity, A]) {
  def HACK_OVERRIDE_unsafeGet(): A = lifecycle.unsafeGet()
}
```

```scala mdoc:override:to-string
import distage.Injector

trait App {
  def interpret(input: String): String
}
object App {
  final class Impl(
    handlers: Set[CommandHandler]
  ) extends App {
    override def interpret(input: String): String = {
      handlers.map(_.handle).reduce(_ orElse _).lift(input) match {
        case Some(answer) => s"ANSWER: $answer"
        case None         => "?"
      }
    }
  }
}

object AppModule extends ModuleDef {
  // include all the previous module definitions
  include(AdditionModule)
  include(SubtractionModule)

  // add a help handler
  many[CommandHandler].add(CommandHandler {
    case "help" => "Please input an arithmetic expression!"
  })

  // bind App
  make[App].from[App.Impl]
}

// wire the graph and get the app

val app = Injector().produceGet[App](AppModule).HACK_OVERRIDE_unsafeGet()

// check how it works

app.interpret("1 + 5")

app.interpret("7 - 11")

app.interpret("1 / 3")

app.interpret("help")
```

If we rewire the app without `SubtractionModule`, it will expectedly lose the ability to subtract:

```scala mdoc:override:to-string
Injector().HACK_OVERRIDE_produceRun(AppModule -- SubtractionModule.keys) {
  app: App =>
    app.interpret("10 - 1")
}
```

Further reading:

- Guice calls the same concept ["Multibindings"](https://github.com/google/guice/wiki/Multibindings).

## Mutator Bindings

Mutations can be attached to any component using `modify[X]` keyword.

If present, they will be applied in an undefined order after the component has been created, but _before_ it is visible to any other component.

Mutators provide a way to do partial overrides or slight modifications of some existing component without redefining it fully.

Example:

```scala mdoc:reset:to-string
import distage.{Id, Injector, ModuleDef}

def startingModule = new ModuleDef {
  make[Int].fromValue(1) // 1
}

def increment2 = new ModuleDef {
  modify[Int](_ + 1) // 2
  modify[Int](_ + 1) // 3
}

def incrementWithDep = new ModuleDef {
  make[String].fromValue("hello")
  make[Int].named("a-few").fromValue(2)

  // mutators may use other components and add additional dependencies
  modify[Int].by(_.flatAp {
    (s: String, few: Int @Id("a-few")) => currentInt: Int =>
      s.length + few + currentInt
  }) // 5 + 2 + 3
}

Injector().produceRun(
  startingModule ++
  increment2 ++
  incrementWithDep
)((currentInt: Int) => currentInt): Int
```

Another example: Suppose you're using a config case class in your @ref[`distage-testkit`](distage-testkit.md) tests, and for one of the test you want to use a modified value for one of the fields in it. Before 1.0 you'd have to duplicate the config binding into a new key and apply the modifying function to it:

```scala mdoc:reset:invisible
import scala.Predef.{identity => modifyingFunction, _}

final case class Config(a: Int, b: Int, z: Int)
```

```scala mdoc:override:to-string
import distage.{Id, ModuleDef}
import distage.config.ConfigModuleDef
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.SpecIdentity

class HACK_OVERRIDE0_MyTest extends SpecIdentity {
  override def config: TestConfig = super.config.copy(
    moduleOverrides = new ConfigModuleDef {
      makeConfig[Config]("config.myconfig").named("duplicate")
      make[Config].from {
        (thatConfig: Config @Id("duplicate")) =>
          modifyingFunction(thatConfig)
      }
    }
  )
}
```

Now instead of overriding the entire binding, we may use a mutator:

```scala mdoc:override:to-string
class HACK_OVERRIDE1_MyTest extends SpecIdentity {
  override def config: TestConfig = super.config.copy(
    moduleOverrides = new ModuleDef {
      modify[Config](modifyingFunction(_))
    }
  )
}
```

Mutators are subject to configuration using @ref[Activation Axis](#activation-axis) and will be applied conditionally, if tagged:

```scala mdoc:to-string
import distage.{Activation, Injector, Mode}

def axisIncrement = new ModuleDef {
  make[Int].fromValue(1)
  modify[Int](_ + 10).tagged(Mode.Test)
  modify[Int](_ + 1).tagged(Mode.Prod)
}

Injector().produceRun(axisIncrement, Activation(Mode -> Mode.Test))((currentInt: Int) => currentInt): Int

Injector().produceRun(axisIncrement, Activation(Mode -> Mode.Prod))((currentInt: Int) => currentInt): Int
```

## Effect Bindings

Sometimes we want to effectfully create a component, but the resulting component or data does not need to be deallocated.
An example might be a global `Semaphore` to limit the parallelism of the entire application based on configuration,
or a test implementation of some service made with `Ref`s.

In these cases we can use `.fromEffect` to create a value using an effectful constructor.

Example with a `Ref`-based Tagless Final `KVStore`:

```scala mdoc:reset:to-string
import distage.{Injector, ModuleDef}
import izumi.functional.bio.{Error2, Primitives2, F}
import zio.{Task, IO}

trait KVStore[F[_, _]] {
  def get(key: String): F[NoSuchElementException, String]
  def put(key: String, value: String): F[Nothing, Unit]
}

def dummyKVStore[F[+_, +_]: Error2: Primitives2]: F[Nothing, KVStore[F]] = {
  for {
    ref <- F.mkRef(Map.empty[String, String])
  } yield new KVStore[F] {
    def put(key: String, value: String): F[Nothing, Unit] = {
      ref.update_(_ + (key -> value))
    }

    def get(key: String): F[NoSuchElementException, String] = {
      for {
        map <- ref.get
        res <- map.get(key) match {
          case Some(value) => F.pure(value)
          case None        => F.fail(new NoSuchElementException(key))
        }
      } yield res
    }
  }
}

def kvStoreModule = new ModuleDef {
  make[KVStore[IO]].fromEffect(dummyKVStore[IO])
}

val io = Injector[Task]()
  .produceRun[String](kvStoreModule) {
    kv: KVStore[IO] =>
      for {
        _    <- kv.put("apple", "pie")
        res1 <- kv.get("apple")
        _    <- kv.put("apple", "ipad")
        res2 <- kv.get("apple")
      } yield res1 + res2
  }

zio.Runtime.default.unsafeRun(io)
```

You need to specify your effect type when constructing `Injector`, as in `Injector[F]()`, to use effect bindings in chosen `F[_]`.

## ZIO Has Bindings

You can inject into ZIO Environment using `make[_].fromHas` syntax for `ZLayer`, `ZManaged`, `ZIO` or any `F[_, _, _]: Local3`:

```scala mdoc:to-string
import zio._
import zio.console.{Console, putStrLn}
import distage.ModuleDef

class Dependency

class X(dependency: Dependency)

def makeX: RIO[Console with Has[Dependency], X] = {
  for {
    dep <- ZIO.service[Dependency]
    _   <- putStrLn(s"Obtained environment dependency = $dep")
  } yield new X(dep)
}

def makeXManaged: RManaged[Console with Has[Dependency], X] = makeX.toManaged_

def makeXLayer: RLayer[Console with Has[Dependency], Has[X]] = makeX.toLayer

def module1 = new ModuleDef {
  make[Dependency]

  make[X].fromHas(makeX)
  // or
  make[X].fromHas(makeXManaged)
  // or
  make[X].fromHas(makeXLayer)
}
```

You can also mix environment and parameter dependencies at the same time in one constructor:

```scala mdoc:to-string
def zioArgEnvCtor(
  dependency: Dependency
): RLayer[Console, Has[X]] = {
  ZLayer.succeed(dependency) ++
  ZLayer.identity[Console] >>>
  makeX.toLayer
}

def module2 = new ModuleDef {
  make[Dependency]

  make[X].fromHas(zioArgEnvCtor _)
}
```

`zio.Has` values are derived at compile-time by @scaladoc[HasConstructor](izumi.distage.constructors.HasConstructor) macro and can be summoned at need.

Another example:

```scala mdoc:reset:to-string
import distage.{Injector, ModuleDef}
import zio.console.{putStrLn, Console}
import zio.{UIO, RIO, Ref, Task, Has}

trait Hello {
  def hello: UIO[String]
}
trait World {
  def world: UIO[String]
}

// Environment forwarders that allow
// using service functions from everywhere

val hello: RIO[Has[Hello], String] = RIO.accessM(_.get.hello)

val world: RIO[Has[World], String] = RIO.accessM(_.get.world)

// service implementations

val makeHello = {
  (for {
    _     <- putStrLn("Creating Enterprise Hellower...")
    hello = new Hello { val hello = UIO("Hello") }
  } yield hello).toManaged(release = _ =>
    putStrLn("Shutting down Enterprise Hellower").orDie
  )
}

val makeWorld = {
  for {
    counter <- Ref.make(0)
  } yield new World {
    val world = counter.get.map(c => if (c < 1) "World" else "THE World")
  }
}

// the main function

val turboFunctionalHelloWorld: RIO[Has[Hello] with Has[World] with Has[Console.Service], Unit] = {
  for {
    hello <- hello
    world <- world
    _     <- putStrLn(s"$hello $world")
  } yield ()
}

def module = new ModuleDef {
  make[Hello].fromHas(makeHello)
  make[World].fromHas(makeWorld)
  make[Unit].fromHas(turboFunctionalHelloWorld)
}

val main = Injector[Task]()
  .produceRun[Unit](module)((_: Unit) => Task.unit)

zio.Runtime.default.unsafeRun(main)
```

### Converting ZIO environment dependencies to parameters

Any ZIO Service that requires an environment can be turned into a service without an environment dependency by providing
the dependency in each method using `.provide`.

This pattern can be generalized by implementing an instance of `cats.Contravariant` (or `cats.tagless.FunctorK`) for your services
and using it to turn environment dependencies into constructor parameters.

In that way ZIO Environment can be used uniformly
for declaration of dependencies, but the dependencies used inside the service do not leak to other services calling it.
See: https://gitter.im/ZIO/Core?at=5dbb06a86570b076740f6db2

Example:

```scala mdoc:reset:to-string
import cats.Contravariant
import distage.{Injector, ModuleDef, Functoid, Tag, TagK, HasConstructor}
import zio.{Task, UIO, URIO, Has}

trait Dependee[-R] {
  def x(y: String): URIO[R, Int]
}
trait Depender[-R] {
  def y: URIO[R, String]
}
implicit val contra1: Contravariant[Dependee] = new Contravariant[Dependee] {
  def contramap[A, B](fa: Dependee[A])(f: B => A): Dependee[B] = new Dependee[B] { def x(y: String) = fa.x(y).provideSome(f) }
}
implicit val contra2: Contravariant[Depender] = new Contravariant[Depender] {
  def contramap[A, B](fa: Depender[A])(f: B => A): Depender[B] = new Depender[B] { def y = fa.y.provideSome(f) }
}

type DependeeR = Has[Dependee[Any]]
type DependerR = Has[Depender[Any]]
object dependee extends Dependee[DependeeR] {
  def x(y: String) = URIO.accessM(_.get.x(y))
}
object depender extends Depender[DependerR] {
  def y = URIO.accessM(_.get.y)
}

// cycle
object dependerImpl extends Depender[DependeeR] {
  def y: URIO[DependeeR, String] = dependee.x("hello").map(_.toString)
}
object dependeeImpl extends Dependee[DependerR] {
  def x(y: String): URIO[DependerR, Int] = {
    if (y == "hello") UIO(5)
    else depender.y.map(y.length + _.length)
  }
}

/** Fulfill the environment dependencies of a service from the object graph */
def fullfill[R: Tag: HasConstructor, M[_]: TagK: Contravariant](service: M[R]): Functoid[M[Any]] = {
  HasConstructor[R]
    .map(depsCakeR => Contravariant[M].contramap(service)(_ => depsCakeR))
}

def module = new ModuleDef {
  make[Depender[Any]].from(fullfill(dependerImpl))
  make[Dependee[Any]].from(fullfill(dependeeImpl))
}

Injector()
  .produceRun(module) {
    HasConstructor[DependeeR].map {
      (for {
        r <- dependee.x("zxc")
        _ <- Task(println(s"result: $r"))
      } yield ()).provide(_)
    }
  }.fold(_ => 1, _ => 0)
```

## Auto-Traits

distage can instantiate traits and structural types. All unimplemented fields in a trait, or a refinement are filled in from the object graph.

Trait implementations are derived at compile-time by @scaladoc[TraitConstructor](izumi.distage.constructors.TraitConstructor) macro
and can be summoned at need.

If a suitable trait is specified as an implementation class for a binding, `TraitConstructor` will be used automatically:

Example:

```scala mdoc:reset-object:to-string
import distage.{ModuleDef, Id, Injector}

trait Trait1 {
  def a: Int @Id("a")
}
trait Trait2 {
  def b: Int @Id("b")
}

/** All methods in this trait are implemented,
  * so a constructor for it will be generated
  * even though it's not a class */
trait Pluser {
  def plus(a: Int, b: Int) = a + b
}

trait PlusedInt {
  def result(): Int
}
object PlusedInt {

  /**
    * Besides the dependency on `Pluser`,
    * this class defines 2 more dependencies
    * to be injected from the object graph:
    *
    * `def a: Int @Id("a")` and
    * `def b: Int @Id("b")`
    *
    * When an abstract type is declared as an implementation,
    * its no-argument abstract defs & vals are considered as
    * dependency parameters by TraitConstructor. (empty-parens and
    * parameterized methods are not considered parameters)
    *
    * Here, using an abstract class directly as an implementation
    * lets us avoid writing a lengthier constructor, like this one:
    *
    * {{{
    *   final class Impl(
    *     pluser: Pluser,
    *     override val a: Int @Id("a"),
    *     override val b: Int @Id("b"),
    *   ) extends PlusedInt with Trait1 with Trait2
    * }}}
    */
  abstract class Impl(
    pluser: Pluser
  ) extends PlusedInt
    with Trait1
    with Trait2 {
    override def result(): Int = {
      pluser.plus(a, b)
    }
  }

}

def module = new ModuleDef {
  make[Int].named("a").from(1)
  make[Int].named("b").from(2)
  make[Pluser]
  make[PlusedInt].from[PlusedInt.Impl]
}

Injector().produceRun(module) {
  plusedInt: PlusedInt =>
    plusedInt.result()
}
```

### @impl annotation

Abstract classes or traits without obvious concrete subclasses
may hinder the readability of a codebase, to mitigate that you may use an optional @scaladoc[@impl](izumi.distage.model.definition.impl)
documenting annotation to aid the reader in understanding your intention.

```scala mdoc:to-string
import distage.impl

@impl abstract class Impl(
  pluser: Pluser
) extends PlusedInt with Trait1 with Trait2 {
  override def result(): Int = {
    pluser.plus(a, b)
  }
}
```

### Avoiding constructors even further

When overriding behavior of a class, you may avoid writing a repeat of its constructor in your sub-class by inheriting
it with a trait instead. Example:

```scala mdoc:to-string
/**
  * Note how we avoid writing a call to the super-constructor
  * of `PlusedInt.Impl`, such as:
  *
  * {{{
  *   abstract class OverridenPlusedIntImpl(
  *     pluser: Pluser
  *   ) extends PlusedInt.Impl(pluser)
  * }}}
  *
  * Which would be unavoidable with class-to-class inheritance.
  * Using trait-to-class inheritance we avoid writing any boilerplate
  * besides the overrides we want to apply to the class.
  */
@impl trait OverridenPlusedIntImpl extends PlusedInt.Impl {
 override def result(): Int = {
   super.result() * 10
 }
}

Injector().produceRun(module overriddenBy new ModuleDef {
  make[PlusedInt].from[OverridenPlusedIntImpl]
}) {
  plusedInt: PlusedInt =>
    plusedInt.result()
}
```

## Auto-Factories

`distage` can instantiate 'factory' classes from suitable traits. This feature is especially useful with `Akka`.
All unimplemented methods _with parameters_ in a trait will be filled by factory methods:

Given a class `ActorFactory`:

```scala mdoc:reset:to-string
import distage.ModuleDef
import java.util.UUID

class SessionStorage

class UserActor(sessionId: UUID, sessionStorage: SessionStorage)

trait ActorFactory {
  // UserActor will be created as follows:
  //   sessionId argument is provided by the user
  //   sessionStorage argument is wired from the object graph
  def createActor(sessionId: UUID): UserActor
}
```

And a binding of `ActorFactory` *without* an implementation

```scala mdoc:to-string
class ActorModule extends ModuleDef {
  make[ActorFactory]
}
```

`distage` will derive and bind the following implementation for `ActorFactory`:

```scala mdoc:to-string
class ActorFactoryImpl(sessionStorage: SessionStorage) extends ActorFactory {
  override def createActor(sessionId: UUID): UserActor = {
    new UserActor(sessionId, sessionStorage)
  }
}
```

### @With annotation

`@With` annotation can be used to specify the implementation class, to avoid leaking the implementation type in factory method result:

```scala mdoc:reset:to-string
import distage.{Injector, ModuleDef, With}

trait Actor {
  def receive(msg: Any): Unit
}

object Actor {
  trait Factory {
    def newActor(id: String): Actor @With[Actor.Impl]
  }

  final class Impl(id: String, config: Actor.Configuration) extends Actor {
    def receive(msg: Any) = {
      val response = s"Actor `$id` received a message: $msg"
      println(if (config.allCaps) response.toUpperCase else response)
    }
  }

  final case class Configuration(allCaps: Boolean)
}

def factoryModule = new ModuleDef {
  make[Actor.Factory]
  make[Actor.Configuration].from(Actor.Configuration(allCaps = false))
}

Injector()
  .produceGet[Actor.Factory](factoryModule)
  .use(_.newActor("Martin Odersky").receive("ping"))
```

You can use this feature to concisely provide non-Singleton semantics for some of your components.

Factory implementations are derived at compile-time by
@scaladoc[FactoryConstructor](izumi.distage.constructors.FactoryConstructor) macro
and can be summoned at need.

## Tagless Final Style

Tagless Final is one of the popular patterns for structuring purely-functional applications.

Brief introduction to tagless final:

- [Deferring Commitments: Tagless Final](https://medium.com/@calvin.l.fer/deferring-commitments-tagless-final-704d768f15cb)
- [Introduction to Tagless Final](https://www.beyondthelines.net/programming/introduction-to-tagless-final/)

Advantages of `distage` as a driver for TF compared to implicits:

- easy explicit overrides
- easy @ref[effectful instantiation](basics.md#effect-bindings) and @ref[resource management](basics.md#resource-bindings-lifecycle)
- extremely easy & scalable @ref[test](distage-testkit.md#testkit) context setup due to the above
- multiple different implementations for a type using disambiguation by `@Id`

For example, let's take [`freestyle`'s tagless example](http://frees.io/docs/core/handlers/#tagless-interpretation)
and make it better by replacing dependencies on global `import`ed implementations with explicit modules.

First, the program we want to write:

```scala mdoc:reset:to-string
import cats.Monad
import cats.effect.{Sync, IO}
import cats.syntax.all._
import distage.{Roots, Module, ModuleDef, Injector, Tag, TagK, TagKK}

trait Validation[F[_]] {
  def minSize(s: String, n: Int): F[Boolean]
  def hasNumber(s: String): F[Boolean]
}
object Validation {
  def apply[F[_]: Validation]: Validation[F] = implicitly
}

trait Interaction[F[_]] {
  def tell(msg: String): F[Unit]
  def ask(prompt: String): F[String]
}
object Interaction {
  def apply[F[_]: Interaction]: Interaction[F] = implicitly
}

class TaglessProgram[F[_]: Monad: Validation: Interaction] {
  def program: F[Unit] = for {
    userInput <- Interaction[F].ask("Give me something with at least 3 chars and a number on it")
    valid     <- (Validation[F].minSize(userInput, 3), Validation[F].hasNumber(userInput)).mapN(_ && _)
    _         <- if (valid) Interaction[F].tell("awesomesauce!")
                 else       Interaction[F].tell(s"$userInput is not valid")
  } yield ()
}

def ProgramModule[F[_]: TagK: Monad] = new ModuleDef {
  make[TaglessProgram[F]]
}
```

@scaladoc[TagK](izumi.reflect.TagK) is `distage`'s analogue of `TypeTag` for higher-kinded types such as `F[_]`,
it allows preserving type-information at runtime for type parameters.
You'll need to add a @scaladoc[TagK](izumi.reflect.TagK) context bound to create a module parameterized by an abstract `F[_]`.
To parameterize by non-higher-kinded types, use just @scaladoc[Tag](izumi.reflect.Tag).

Now the interpreters for `Validation` and `Interaction`:

```scala mdoc:to-string
final class SyncValidation[F[_]](implicit F: Sync[F]) extends Validation[F] {
  def minSize(s: String, n: Int): F[Boolean] = F.delay(s.size >= n)
  def hasNumber(s: String): F[Boolean]       = F.delay(s.exists(c => "0123456789".contains(c)))
}

final class SyncInteraction[F[_]](implicit F: Sync[F]) extends Interaction[F] {
  def tell(s: String): F[Unit]  = F.delay(println(s))
  def ask(s: String): F[String] = F.delay("This could have been user input 1")
}

def SyncInterpreters[F[_]: TagK: Sync] = {
  new ModuleDef {
    make[Validation[F]].from[SyncValidation[F]]
    make[Interaction[F]].from[SyncInteraction[F]]
  }
}

// combine all modules

def SyncProgram[F[_]: TagK: Sync] = ProgramModule[F] ++ SyncInterpreters[F]

// create object graph Lifecycle

val objectsLifecycle = Injector[IO]().produce(SyncProgram[IO], Roots.Everything)

// run

objectsLifecycle.use(_.get[TaglessProgram[IO]].program).unsafeRunSync()
```

### Effect-type polymorphism

The program module is polymorphic over effect type. It can be instantiated by a different effect:

```scala mdoc:to-string
import zio.interop.catz._
import zio.Task

val ZIOProgram = ProgramModule[Task] ++ SyncInterpreters[Task]
```

We may even choose different interpreters at runtime:

```scala mdoc:to-string
import zio.RIO
import zio.console.{Console, getStrLn, putStrLn}
import distage.Activation

object RealInteractionZIO extends Interaction[RIO[Console, _]] {
  def tell(s: String): RIO[Console, Unit]  = putStrLn(s)
  def ask(s: String): RIO[Console, String] = putStrLn(s) *> getStrLn
}

def RealInterpretersZIO = {
  SyncInterpreters[RIO[Console, _]] overriddenBy new ModuleDef {
    make[Interaction[RIO[Console, _]]].from(RealInteractionZIO)
  }
}

def chooseInterpreters(isDummy: Boolean) = {
  val interpreters = if (isDummy) SyncInterpreters[RIO[Console, _]]
                     else         RealInterpretersZIO
  def module = ProgramModule[RIO[Console, _]] ++ interpreters

  Injector[RIO[Console, _]]()
    .produceGet[TaglessProgram[RIO[Console, _]]](module, Activation.empty)
}

// execute

chooseInterpreters(true)
```

### Kind polymorphism

Modules can be polymorphic over arbitrary kinds - use `TagKK` to abstract over bifunctors:

```scala mdoc:to-string
class BifunctorIOModule[F[_, _]: TagKK] extends ModuleDef
```

Or use `Tag.auto.T` to abstract over any kind:

```scala mdoc:to-string
class MonadTransModule[F[_[_], _]: Tag.auto.T] extends ModuleDef
```

```scala mdoc:to-string
class TrifunctorModule[F[_, _, _]: Tag.auto.T] extends ModuleDef
```

```scala mdoc:to-string
class EldritchModule[F[+_, -_[_, _], _[_[_, _], _], _]: Tag.auto.T] extends ModuleDef
```

consult [izumi.reflect.HKTag](https://javadoc.io/doc/dev.zio/izumi-reflect_2.13/latest/izumi/reflect/HKTag.html) docs for more details.

## Cats & ZIO Integration

Cats & ZIO instances and syntax are available automatically in `distage-core`, without wildcard imports, if your project depends on `cats-core`, `cats-effect` or `zio`.
However, distage *won't* bring in `cats` or `zio` as dependencies if you don't already depend on them.
(see [No More Orphans](https://blog.7mind.io/no-more-orphans.html) blog post for details on how that works)

@ref[Cats Resource & ZIO ZManaged Bindings](basics.md#resource-bindings-lifecycle) also work out of the box without any magic imports.

All relevant typeclass instances for chosen effect type, such as `ConcurrentEffect[F]`, are @ref[included by default](basics.md#out-of-the-box-typeclass-instances) (overridable by user bindings)
