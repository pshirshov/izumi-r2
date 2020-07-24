package izumi.distage.roles.test.fixtures

import distage.LocatorRef
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.Axis
import izumi.distage.model.effect.DIEffect
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks._

import scala.collection.mutable

object Fixture {
  trait Dummy

  case class TestServiceConf2(
    strval: String
  )

  case class TestServiceConf(
    intval: Int,
    strval: String,
    overridenInt: Int,
    systemPropInt: Int,
    systemPropList: List[Int],
  )

  class XXX_ResourceEffectsRecorder[F[_]] {
    private val startedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
    private val closedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
    private val checkedResources: mutable.ArrayBuffer[IntegrationCheck[F]] = mutable.ArrayBuffer()

    def onStart(c: AutoCloseable): Unit = this.synchronized(startedCloseables += c).discard()
    def onClose(c: AutoCloseable): Unit = this.synchronized(closedCloseables += c).discard()
    def onCheck(c: IntegrationCheck[F]): Unit = this.synchronized(checkedResources += c).discard()

    def getStartedCloseables(): Seq[AutoCloseable] = this.synchronized(startedCloseables.toList)
    def getClosedCloseables(): Seq[AutoCloseable] = this.synchronized(closedCloseables.toList)
    def getCheckedResources(): Seq[IntegrationCheck[F]] = this.synchronized(checkedResources.toList)
  }

  case class XXX_LocatorLeak(locatorRef: LocatorRef)

  trait TestResource

  trait ProbeResource[F[_]] extends TestResource with AutoCloseable {
    def counter: XXX_ResourceEffectsRecorder[F]
    counter.onStart(this)

    override def close(): Unit = counter.onClose(this)

  }

  abstract class ProbeCheck[F[_]: DIEffect] extends ProbeResource[F] with IntegrationCheck[F] {
    override def resourcesAvailable(): F[ResourceCheck] = DIEffect[F].maybeSuspend {
      counter.onCheck(this)
      ResourceCheck.Success()
    }
  }

  class IntegrationResource0[F[_]: DIEffect](val closeable: IntegrationResource1[F], val counter: XXX_ResourceEffectsRecorder[F]) extends ProbeCheck[F]
  class IntegrationResource1[F[_]: DIEffect](val roleComponent: JustResource1[F], val counter: XXX_ResourceEffectsRecorder[F]) extends ProbeCheck[F]

  case class ProbeResource0[F[_]: DIEffect](roleComponent: JustResource3[F], counter: XXX_ResourceEffectsRecorder[F]) extends ProbeResource[F]

  case class JustResource1[F[_]: DIEffect](roleComponent: JustResource2[F], counter: XXX_ResourceEffectsRecorder[F]) extends TestResource
  case class JustResource2[F[_]: DIEffect](closeable: ProbeResource0[F], counter: XXX_ResourceEffectsRecorder[F]) extends TestResource
  case class JustResource3[F[_]: DIEffect](counter: XXX_ResourceEffectsRecorder[F]) extends TestResource

  trait AxisComponent
  object AxisComponentIncorrect extends AxisComponent
  object AxisComponentCorrect extends AxisComponent

  object AxisComponentAxis extends Axis {
    case object Incorrect extends AxisValueDef
    case object Correct extends AxisValueDef
  }

  case class ListConf(ints: List[Int])

}
