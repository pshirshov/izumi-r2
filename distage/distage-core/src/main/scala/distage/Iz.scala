package distage

import izumi.distage.bootstrap.{BootstrapLocator, CglibBootstrap}
import izumi.distage.model.definition.BootstrapContextModule
import izumi.distage.model.effect.DIEffect
import izumi.distage.model.effect.DIEffect.syntax._

sealed trait Iz[F[_], T] {
}

object Iz {

  case class State(bootstrapLocator: Locator)

  case class DeclareModule[F[_]](module: PlannerInput, in: Option[Locator]) {
    def flatMap[T1](f: OrderedPlan => Iz[F, T1]): Iz[F, T1] = {
      FMPlan(module, in, f)
    }
  }

  case class FMPlan[F[_], T1](
                               input: PlannerInput,
                               in: Option[Locator],
                               f: OrderedPlan => Iz[F, T1]
                             ) extends Iz[F, T1]

  case class CreateSubcontext[F[_]](plan: OrderedPlan, in: Option[Locator]) {
    def flatMap[T1](f: Locator => Iz[F, T1]): Iz[F, T1] = {
      FMProduce(plan, in, f)
    }
  }


  case class FMProduce[F[_], T1](
                                  plan: OrderedPlan,
                                  in: Option[Locator],
                                  f: Locator => Iz[F, T1]
                                ) extends Iz[F, T1]

  case class FlatMapValue[F[_], T0, T1](
                                         prev: Iz[F, T0],
                                         f: T0 => Iz[F, T1]
                                       ) extends Iz[F, T1]

  case class Map[F[_], T, T1](
                               prev: Iz[F, T],
                               f: T => T1
                             ) extends Iz[F, T1]


  case class UseLocator[F[_], T](locator: Locator, t: Locator => T) extends Iz[F, T] {
    def map[T1](f: T => T1): Iz[F, T1] = {
      UseLocator(locator, l => f(t(l)))
    }

    def flatMap[T1](f: T => Iz[F, T1]): Iz[F, T1] = {
      FlatMapValue(this, f)
    }
  }

  class Suspend[F[_], T1](c: => T1) extends Iz[F, T1] {
    def flatMap[B](f: T1 => Iz[F, B]): Iz[F, B] = {
      FlatMapValue[F, T1, B](this, f)
    }

    def value: T1 = c
  }


  def plan[F[_]](module: PlannerInput): DeclareModule[F] = new DeclareModule[F](module, None)

  def plan[F[_]](module: PlannerInput, in: Locator): DeclareModule[F] = new DeclareModule[F](module, Some(in))

  def produce[F[_]](plan: OrderedPlan): CreateSubcontext[F] = new CreateSubcontext[F](plan, None)

  def produce[F[_]](plan: OrderedPlan, in: Locator): CreateSubcontext[F] = new CreateSubcontext[F](plan, Some(in))

  def useLocator[F[_], T](locator: Locator)(f: Locator => T): UseLocator[F, T] = new UseLocator[F, T](locator, f)

  def use[F[_], T](locator: Locator)(f: ProviderMagnet[T]): UseLocator[F, T] = new UseLocator[F, T](locator, _.run(f))

  def suspend[F[_], T](v: T): Suspend[F, T] = new Suspend[F, T](v)

  def run[F[_] : DIEffect : TagK, T](
                                      injectorF: Iz[F, T],
                                      bootstrap: BootstrapContextModule = CglibBootstrap.cogenBootstrap,
                                      overrides: BootstrapModule = BootstrapModule.empty,
                                    ): F[T] = {
    val F = implicitly[DIEffect[F]]

    def interpret[T1, B](state: State, i: Iz[F, T1]): F[B] = {
      val out: F[B] = i match {

        case fm: FMPlan[F, T1]@unchecked =>
          for {
            plan <- F.maybeSuspend {
              Injector.inherit(fm.in.getOrElse(state.bootstrapLocator)).plan(fm.input)
            }
            p <- F.maybeSuspend(interpret[T1, B](state, fm.f(plan)))
            out <- p
          } yield {
            out
          }

        case fm: FMProduce[F, T1]@unchecked =>
          for {
            loc <- F.maybeSuspend {
              Injector.inherit(fm.in.getOrElse(state.bootstrapLocator))
                .produceF[F](fm.plan)
            }
            out <- loc.use {
              newLocator =>
                interpret[T1, B](state, fm.f(newLocator))
            }
          } yield {
            out
          }

        case fm: FlatMapValue[F, T1, B]@unchecked =>
          for {
            p <- F.maybeSuspend(interpret[T1, T1](state, fm.prev))
            v <- p
            i = fm.f.apply(v)
            out <- F.maybeSuspend(interpret[B, B](state, i))
            r <- out
          } yield {
            r
          }

        case m: Map[F, T1, B]@unchecked =>
          for {
            p <- F.maybeSuspend(interpret[T1, T1](state, m.prev).map(m.f))
            out <- p
          } yield {
            out
          }

        case u: UseLocator[F, B]@unchecked =>
          for {
            out <- F.maybeSuspend(u.t(u.locator))
          } yield {
            out
          }

        case u: Suspend[F, B]@unchecked =>
          for {
            out <- F.maybeSuspend(u.value)
          } yield {
            out
          }
      }
      out
    }


    val bootstrapDefinition = bootstrap.overridenBy(overrides)
    val bootstrapLocator = new BootstrapLocator(bootstrapDefinition overridenBy new BootstrapModuleDef {
      make[BootstrapModule].fromValue(bootstrapDefinition)
    })
    val state = State(bootstrapLocator)
    interpret(state, injectorF)
  }
}

