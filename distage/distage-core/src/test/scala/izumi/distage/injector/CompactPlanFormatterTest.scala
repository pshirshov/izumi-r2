package izumi.distage.injector

import distage.ModuleDef
import izumi.distage.fixtures.BasicCases.BasicCase1.{Impl1, JustTrait}
import izumi.distage.fixtures.HigherKindCases.HigherKindsCase1.OptionT
import izumi.distage.injector.CompactPlanFormatterTest._
import izumi.distage.model.PlannerInput
import izumi.functional.Renderable._
import org.scalatest.wordspec.AnyWordSpec

object CompactPlanFormatterTest {
  trait T1[A, B]

  class K1[F[_, _]]

  object W1 {
    trait T2
  }

  object W2 {
    trait T2
  }
}

class CompactPlanFormatterTest extends AnyWordSpec with MkInjector {
  "PlanFormatterTest should produce short class names if it's unique in plan" in {
    val injector = mkInjector()
    val plan = injector.plan(PlannerInput.everything(new ModuleDef {
      make[JustTrait].from[Impl1]
      make[OptionT[scala.Either[Nothing, _], Unit]].from(OptionT[Either[Nothing, _], Unit](Right(None)))
      make[K1[T1]].from(new K1[T1] {})
      make[W1.T2]
      make[W2.T2]
    }))

    val formatted = plan.render().replaceAll("\u001B\\[[;\\d]*m", "")
    assert(!formatted.contains(classOf[Impl1].getName))
    assert(formatted.contains("{type.BasicCases::BasicCase1::JustTrait}"))
    assert(formatted.contains("BasicCases::BasicCase1::Impl1"))
    assert(formatted.contains("{type.HigherKindCases::HigherKindsCase1::OptionT[=λ %1:0 → Either[+Nothing,+1:0],=Unit]}"))
    assert(formatted.contains("{type.CompactPlanFormatterTest::W1::T2}"))
    assert(formatted.contains("{type.CompactPlanFormatterTest::K1[=λ %1:0,%1:1 → CompactPlanFormatterTest::T1[=1:0,=1:1]]}"))
  }
}
