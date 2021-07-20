import izumi.distage.planning.{extensions, solver}
import izumi.distage.{constructors, model, modules, planning}

package object distage extends Distage {

  override type ModuleDef = model.definition.ModuleDef

  override type Injector[F[_]] = model.Injector[F]
  override type Planner = model.Planner
  override type Producer = model.Producer

  override type Activation = model.definition.Activation
  override val Activation: model.definition.Activation.type = model.definition.Activation

  override type Roots = model.plan.Roots
  override val Roots: model.plan.Roots.type = model.plan.Roots

  override type Locator = model.Locator
  override val Locator: model.Locator.type = model.Locator

  override type LocatorRef = model.recursive.LocatorRef

  override type PlanVerifier = solver.PlanVerifier
  override val PlanVerifier: solver.PlanVerifier.type = solver.PlanVerifier

  override type DefaultModule[F[_]] = modules.DefaultModule[F]
  override val DefaultModule: modules.DefaultModule.type = modules.DefaultModule

  override type DefaultModule2[F[_, _]] = modules.DefaultModule2[F]
  override val DefaultModule2: modules.DefaultModule2.type = modules.DefaultModule2

  override type DefaultModule3[F[_, _, _]] = modules.DefaultModule3[F]
  override val DefaultModule3: modules.DefaultModule3.type = modules.DefaultModule3

  override type LocatorDef = model.definition.LocatorDef

  override type Id = model.definition.Id
  override type With[T] = model.definition.With[T]
  override type impl = model.definition.impl

  override type Tag[T] = izumi.reflect.Tag[T]
  override val Tag: izumi.reflect.Tag.type = izumi.reflect.Tag

  override type TagK[T[_]] = izumi.reflect.TagK[T]
  override val TagK: izumi.reflect.TagK.type = izumi.reflect.TagK

  override type Lifecycle[+F[_], +Resource] = model.definition.Lifecycle[F, Resource]
  override val Lifecycle: model.definition.Lifecycle.type = model.definition.Lifecycle

  override type Lifecycle2[+F[+_, +_], +E, +A] = model.definition.Lifecycle[F[E, _], A]

  override type Lifecycle3[+F[-_, +_, +_], -R, +E, +A] = model.definition.Lifecycle[F[R, E, _], A]

  override type Axis = model.definition.Axis
  override val Axis: model.definition.Axis.type = model.definition.Axis

  override val StandardAxis: model.definition.StandardAxis.type = model.definition.StandardAxis
  override val Mode: model.definition.StandardAxis.Mode.type = model.definition.StandardAxis.Mode
  override val Repo: model.definition.StandardAxis.Repo.type = model.definition.StandardAxis.Repo
  override val World: model.definition.StandardAxis.World.type = model.definition.StandardAxis.World
  override val Scene: model.definition.StandardAxis.Scene.type = model.definition.StandardAxis.Scene

  override type DIKey = model.reflection.DIKey
  override val DIKey: model.reflection.DIKey.type = model.reflection.DIKey

  override type Functoid[+A] = model.providers.Functoid[A]
  override val Functoid: model.providers.Functoid.type = model.providers.Functoid

  override type AnyConstructor[T] = constructors.AnyConstructor[T]
  override val AnyConstructor: constructors.AnyConstructor.type = constructors.AnyConstructor

  override type ClassConstructor[T] = constructors.ClassConstructor[T]
  override val ClassConstructor: constructors.ClassConstructor.type = constructors.ClassConstructor

  override type TraitConstructor[T] = constructors.TraitConstructor[T]
  override val TraitConstructor: constructors.TraitConstructor.type = constructors.TraitConstructor

  override type FactoryConstructor[T] = constructors.FactoryConstructor[T]
  override val FactoryConstructor: constructors.FactoryConstructor.type = constructors.FactoryConstructor

  override type HasConstructor[T] = constructors.HasConstructor[T]
  override val HasConstructor: constructors.HasConstructor.type = constructors.HasConstructor

  override type BindingTag = model.definition.BindingTag
  override val BindingTag: model.definition.BindingTag.type = model.definition.BindingTag

  override type PlannerInput = model.PlannerInput
  override val PlannerInput: model.PlannerInput.type = model.PlannerInput

  override type GraphDumpObserver = extensions.GraphDumpObserver
  override val GraphDumpObserver: extensions.GraphDumpObserver.type = extensions.GraphDumpObserver

  override type GraphDumpBootstrapModule = extensions.GraphDumpBootstrapModule
  override val GraphDumpBootstrapModule: extensions.GraphDumpBootstrapModule.type = extensions.GraphDumpBootstrapModule

  override type DIPlan = model.plan.DIPlan
  override val DIPlan: model.plan.DIPlan.type = model.plan.DIPlan

  override type SafeType = model.reflection.SafeType
  override val SafeType: model.reflection.SafeType.type = model.reflection.SafeType

  override type ModuleBase = model.definition.ModuleBase
  override val ModuleBase: model.definition.ModuleBase.type = model.definition.ModuleBase

  override type Module = model.definition.Module
  override val Module: model.definition.Module.type = model.definition.Module

  override type BootstrapModule = model.definition.BootstrapModule
  override val BootstrapModule: model.definition.BootstrapModule.type = model.definition.BootstrapModule

  override type BootstrapModuleDef = model.definition.BootstrapModuleDef

  override type AutoSetModule = planning.AutoSetModule
  override val AutoSetModule: planning.AutoSetModule.type = planning.AutoSetModule

  override type TagKK[T[_, _]] = izumi.reflect.TagKK[T]
  override val TagKK: izumi.reflect.TagKK.type = izumi.reflect.TagKK

  override type TagK3[T[_, _, _]] = izumi.reflect.TagK3[T]
  override val TagK3: izumi.reflect.TagK3.type = izumi.reflect.TagK3

  override type TagT[T[_[_]]] = izumi.reflect.TagT[T]
  override val TagT: izumi.reflect.TagT.type = izumi.reflect.TagT

  override type TagTK[T[_[_], _]] = izumi.reflect.TagTK[T]
  override val TagTK: izumi.reflect.TagTK.type = izumi.reflect.TagTK

  override type TagTKK[T[_[_], _, _]] = izumi.reflect.TagTKK[T]
  override val TagTKK: izumi.reflect.TagTKK.type = izumi.reflect.TagTKK

  override type TagTK3[T[_[_], _, _, _]] = izumi.reflect.TagTK3[T]
  override val TagTK3: izumi.reflect.TagTK3.type = izumi.reflect.TagTK3

}
