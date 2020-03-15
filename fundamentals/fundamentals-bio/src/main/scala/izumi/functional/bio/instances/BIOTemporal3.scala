package izumi.functional.bio.instances

import izumi.functional.bio.BIOTemporal
import izumi.functional.bio.impl.BIOTemporalZio
import zio.ZIO

import scala.concurrent.duration.{Duration, FiniteDuration}

trait BIOTemporal3[F[-_, +_, +_]] extends BIOAsync3[F] with BIOTemporalInstances {
  def sleep(duration: Duration): F[Any, Nothing, Unit]
  def timeout[R, E, A](r: F[R, E, A])(duration: Duration): F[R, E, Option[A]]
  def retryOrElse[R, A, E, A2 >: A, E2](r: F[R, E, A])(duration: FiniteDuration, orElse: => F[R, E2, A2]): F[R, E2, A2]

  @inline final def repeatUntil[R, E, A](action: F[R, E, Option[A]])(onTimeout: => E, sleep: FiniteDuration, maxAttempts: Int): F[R, E, A] = {
    def go(n: Int): F[R, E, A] = {
      flatMap(action) {
        case Some(value) =>
          pure(value)
        case None =>
          if (n <= maxAttempts) {
            *>(this.sleep(sleep), go(n + 1))
          } else {
            fail(onTimeout)
          }
      }
    }

    go(0)
  }
}

private[bio] sealed trait BIOTemporalInstances
object BIOTemporalInstances {
  implicit def BIOTemporalZio[R](implicit clockService: zio.clock.Clock): BIOTemporal[ZIO[R, +?, +?]] = new BIOTemporalZio(clockService).asInstanceOf[BIOTemporal[ZIO[R, +?, +?]]]
  implicit def BIOTemporal3Zio(implicit clockService: zio.clock.Clock): BIOTemporal3[ZIO] = new BIOTemporalZio(clockService)
}