package izumi.distage.model.provisioning

import izumi.distage.model.Locator
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.exceptions._
import izumi.distage.model.plan.repr.OpFormatter
import izumi.distage.model.plan.DIPlan
import izumi.distage.model.provisioning.PlanInterpreter.{FailedProvision, FinalizerFilter}
import izumi.distage.model.provisioning.Provision.ProvisionImmutable
import izumi.distage.model.reflection._
import izumi.fundamentals.platform.exceptions.IzThrowable._
import izumi.fundamentals.platform.strings.IzString._
import izumi.reflect.TagK

import scala.concurrent.duration.Duration

trait PlanInterpreter {
  def run[F[_]: TagK: QuasiIO](
    plan: DIPlan,
    parentLocator: Locator,
    filterFinalizers: FinalizerFilter[F],
  ): Lifecycle[F, Either[FailedProvision[F], Locator]]
}

object PlanInterpreter {
  trait FinalizerFilter[F[_]] {
    def filter(finalizers: collection.Seq[Finalizer[F]]): collection.Seq[Finalizer[F]]
  }
  object FinalizerFilter {
    def all[F[_]]: FinalizerFilter[F] = identity
  }

  final case class Finalizer[+F[_]](key: DIKey, effect: () => F[Unit], fType: SafeType)
  object Finalizer {
    def apply[F[_]: TagK](key: DIKey, effect: () => F[Unit]): Finalizer[F] = {
      new Finalizer(key, effect, SafeType.getK[F])
    }
  }

  case class FailedProvisionMeta(timings: Map[DIKey, Duration])

  final case class FailedProvision[F[_]](
    failed: ProvisionImmutable[F],
    plan: DIPlan,
    parentContext: Locator,
    failures: Seq[ProvisioningFailure],
    meta: FailedProvisionMeta,
    fullStackTraces: Boolean,
  ) {
    def throwException[A]()(implicit F: QuasiIO[F]): F[A] = {
      val repr = failures
        .map {
          case AggregateFailure(failures) =>
            val messages = failures
              .map {
                case MissingImport(op) =>
                  MissingInstanceException.format(op.target, op.references)
                case IncompatibleEffectTypesException(op, provisionerEffectType, actionEffectType) =>
                  IncompatibleEffectTypesException.format(op, provisionerEffectType, actionEffectType)
              }
              .niceMultilineList("[!]")
            s"Unsatisfied preconditions:\n$messages"
          case StepProvisioningFailure(op, f) =>
            val pos = OpFormatter.formatBindingPosition(op.origin)
            val name = f match {
              case di: DIException => di.getClass.getSimpleName
              case o => o.getClass.getName
            }
            val trace = if (fullStackTraces) f.stackTrace else f.getMessage
            s"${op.target} $pos, $name: $trace"
        }

      val ccFailed = failures
        .flatMap {
          case AggregateFailure(failures) =>
            failures.flatMap {
              case f: MissingImport =>
                f.op.references
              case f: IncompatibleEffectTypesException =>
                Seq(f.op.target)
            }
          case op: StepProvisioningFailure =>
            Seq(op.op.target)
        }.toSet.size

      val ccDone = failed.instances.size
      val ccTotal = plan.steps.size

      import izumi.fundamentals.platform.exceptions.IzThrowable._
      import izumi.fundamentals.platform.strings.IzString._

      val exceptions = failures.flatMap {
        case _: AggregateFailure =>
          Seq.empty
        case f: StepProvisioningFailure =>
          Seq(f.failure)
      }

      F.fail {
        new ProvisioningException(
          s"""Provisioner failed on $ccFailed of $ccTotal required operations, just $ccDone succeeded:
             |${repr.niceMultilineList()}
             |""".stripMargin,
          null,
        )
          .addAllSuppressed(exceptions)
      }
    }
  }

  object FailedProvision {
    implicit final class FailedProvisionExt[F[_]](private val p: Either[FailedProvision[F], Locator]) extends AnyVal {
      def throwOnFailure()(implicit F: QuasiIO[F]): F[Locator] = p.fold(_.throwException(), F.pure)
    }
  }

}
