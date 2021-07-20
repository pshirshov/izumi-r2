package distage

import izumi.distage.planning.{extensions, solver}
import izumi.distage.{constructors, model, modules, planning}

trait Distage {

  type ModuleDef = model.definition.ModuleDef

  type Injector[F[_]] = model.Injector[F]
  type Planner = model.Planner
  type Producer = model.Producer

  type Activation = model.definition.Activation
  val Activation: model.definition.Activation.type = model.definition.Activation

  type Roots = model.plan.Roots
  val Roots: model.plan.Roots.type = model.plan.Roots

  type Locator = model.Locator
  val Locator: model.Locator.type = model.Locator

  type LocatorRef = model.recursive.LocatorRef

  type PlanVerifier = solver.PlanVerifier
  val PlanVerifier: solver.PlanVerifier.type = solver.PlanVerifier

  type DefaultModule[F[_]] = modules.DefaultModule[F]
  val DefaultModule: modules.DefaultModule.type = modules.DefaultModule

  type DefaultModule2[F[_, _]] = modules.DefaultModule2[F]
  val DefaultModule2: modules.DefaultModule2.type = modules.DefaultModule2

  type DefaultModule3[F[_, _, _]] = modules.DefaultModule3[F]
  val DefaultModule3: modules.DefaultModule3.type = modules.DefaultModule3

  type LocatorDef = model.definition.LocatorDef

  type Id = model.definition.Id
  type With[T] = model.definition.With[T]
  type impl = model.definition.impl

  type Tag[T] = izumi.reflect.Tag[T]
  val Tag: izumi.reflect.Tag.type = izumi.reflect.Tag

  type TagK[T[_]] = izumi.reflect.TagK[T]
  val TagK: izumi.reflect.TagK.type = izumi.reflect.TagK

  type Lifecycle[+F[_], +A] = model.definition.Lifecycle[F, A]
  val Lifecycle: model.definition.Lifecycle.type = model.definition.Lifecycle

  type Lifecycle2[+F[+_, +_], +E, +A] = model.definition.Lifecycle[F[E, _], A]

  type Lifecycle3[+F[-_, +_, +_], -R, +E, +A] = model.definition.Lifecycle[F[R, E, _], A]

  type Axis = model.definition.Axis
  val Axis: model.definition.Axis.type = model.definition.Axis

  val StandardAxis: model.definition.StandardAxis.type = model.definition.StandardAxis
  val Mode: model.definition.StandardAxis.Mode.type = model.definition.StandardAxis.Mode
  val Repo: model.definition.StandardAxis.Repo.type = model.definition.StandardAxis.Repo
  val World: model.definition.StandardAxis.World.type = model.definition.StandardAxis.World
  val Scene: model.definition.StandardAxis.Scene.type = model.definition.StandardAxis.Scene

  type DIKey = model.reflection.DIKey
  val DIKey: model.reflection.DIKey.type = model.reflection.DIKey

  type Functoid[+A] = model.providers.Functoid[A]
  val Functoid: model.providers.Functoid.type = model.providers.Functoid

  type AnyConstructor[T] = constructors.AnyConstructor[T]
  val AnyConstructor: constructors.AnyConstructor.type = constructors.AnyConstructor

  type ClassConstructor[T] = constructors.ClassConstructor[T]
  val ClassConstructor: constructors.ClassConstructor.type = constructors.ClassConstructor

  type TraitConstructor[T] = constructors.TraitConstructor[T]
  val TraitConstructor: constructors.TraitConstructor.type = constructors.TraitConstructor

  type FactoryConstructor[T] = constructors.FactoryConstructor[T]
  val FactoryConstructor: constructors.FactoryConstructor.type = constructors.FactoryConstructor

  type HasConstructor[T] = constructors.HasConstructor[T]
  val HasConstructor: constructors.HasConstructor.type = constructors.HasConstructor

  type BindingTag = model.definition.BindingTag
  val BindingTag: model.definition.BindingTag.type = model.definition.BindingTag

  type PlannerInput = model.PlannerInput
  val PlannerInput: model.PlannerInput.type = model.PlannerInput

  type GraphDumpObserver = extensions.GraphDumpObserver
  val GraphDumpObserver: extensions.GraphDumpObserver.type = extensions.GraphDumpObserver

  type GraphDumpBootstrapModule = extensions.GraphDumpBootstrapModule
  val GraphDumpBootstrapModule: extensions.GraphDumpBootstrapModule.type = extensions.GraphDumpBootstrapModule

  type DIPlan = model.plan.DIPlan
  val DIPlan: model.plan.DIPlan.type = model.plan.DIPlan

  type SafeType = model.reflection.SafeType
  val SafeType: model.reflection.SafeType.type = model.reflection.SafeType

  type ModuleBase = model.definition.ModuleBase
  val ModuleBase: model.definition.ModuleBase.type = model.definition.ModuleBase

  type Module = model.definition.Module
  val Module: model.definition.Module.type = model.definition.Module

  type BootstrapModule = model.definition.BootstrapModule
  val BootstrapModule: model.definition.BootstrapModule.type = model.definition.BootstrapModule

  type BootstrapModuleDef = model.definition.BootstrapModuleDef

  type AutoSetModule = planning.AutoSetModule
  val AutoSetModule: planning.AutoSetModule.type = planning.AutoSetModule

  type TagKK[T[_, _]] = izumi.reflect.TagKK[T]
  val TagKK: izumi.reflect.TagKK.type = izumi.reflect.TagKK

  type TagK3[T[_, _, _]] = izumi.reflect.TagK3[T]
  val TagK3: izumi.reflect.TagK3.type = izumi.reflect.TagK3

  type TagT[T[_[_]]] = izumi.reflect.TagT[T]
  val TagT: izumi.reflect.TagT.type = izumi.reflect.TagT

  type TagTK[T[_[_], _]] = izumi.reflect.TagTK[T]
  val TagTK: izumi.reflect.TagTK.type = izumi.reflect.TagTK

  type TagTKK[T[_[_], _, _]] = izumi.reflect.TagTKK[T]
  val TagTKK: izumi.reflect.TagTKK.type = izumi.reflect.TagTKK

  type TagTK3[T[_[_], _, _, _]] = izumi.reflect.TagTK3[T]
  val TagTK3: izumi.reflect.TagTK3.type = izumi.reflect.TagTK3

  @deprecated("Use `distage.Functoid` instead of `distage.ProviderMagnet`", "1.0")
  type ProviderMagnet[+A] = Functoid[A]
  @deprecated("Use `distage.Functoid` instead of `distage.ProviderMagnet`", "1.0")
  val ProviderMagnet: model.providers.Functoid.type = model.providers.Functoid

  @deprecated("GCMode has been renamed to `Roots`", "old name will be deleted in 1.1.1")
  type GCMode = model.plan.Roots
  @deprecated("GCMode has been renamed to `Roots`", "old name will be deleted in 1.1.1")
  val GCMode: model.plan.Roots.type = model.plan.Roots

  @deprecated("Use distage.Lifecycle.Basic", "1.0")
  type DIResource[+F[_], Resource] = model.definition.Lifecycle.Basic[F, Resource]
  @deprecated("Use distage.Lifecycle", "1.0")
  val DIResource: model.definition.Lifecycle.type = model.definition.Lifecycle

  @deprecated("Use distage.Lifecycle", "1.0")
  type DIResourceBase[+F[_], +Resource] = model.definition.Lifecycle[F, Resource]

}
