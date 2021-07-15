package izumi.distage.injector

import izumi.distage.injector.MissingTagOfTheNestedTypeTest.{NestedTpe, moduleWithoutTag}
import izumi.distage.model.definition.ModuleDef
import izumi.reflect.Tag
import org.scalatest.wordspec.AnyWordSpec

import scala.util.{Failure, Success, Try}

class MissingTagOfTheNestedTypeTest extends AnyWordSpec with MkInjector {
  "fail with no Tag found" in {
    Try{
      new ModuleDef {
        make[Int].from(0)
        include(moduleWithoutTag[NestedTpe.Int.type])
      }
    } match {
      case Failure(exception) if exception.getMessage.contains("is not a type lambda") =>
        fail("Expected failure with missing tag, not a `type lambda` failure.")
      case Success(_) =>
        fail("Injector should fail without implicit Tag[N#TPE]")
      //todo: add check with actual error
      case Failure(_) => ()
    }
  }
}
object MissingTagOfTheNestedTypeTest {
  sealed trait NestedTpe {
    type TPE
  }
  object NestedTpe {
    case object Int extends NestedTpe {
      override type TPE = Int
    }
  }

  final case class Dep[N <: NestedTpe](tpe: N#TPE)

  def moduleWithoutTag[N <: NestedTpe: Tag]: ModuleDef = new ModuleDef {
    make[Dep[N]]
  }
}
