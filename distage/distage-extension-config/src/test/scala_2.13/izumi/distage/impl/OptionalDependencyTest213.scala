package izumi.distage.impl

import izumi.functional.bio.impl.BioEither
import izumi.functional.bio.{Applicative2, ApplicativeError2, Arrow3, ArrowChoice3, Ask3, Async2, Bifunctor2, Bracket2, Concurrent2, Error2, Fork2, Functor2, Guarantee2, IO2, IO3, Local3, Monad2, MonadAsk3, Panic2, Parallel2, Primitives2, PrimitivesM2, Profunctor3, Temporal2}
import org.scalatest.wordspec.AnyWordSpec

class OptionalDependencyTest213 extends AnyWordSpec {

  "Perform exhaustive search for BIO and not find instances for ZIO / monix-bio when they're not on classpath" in {
    final class optSearch2[C[_[+_, +_]]] { def find[F[+_, +_]](implicit a: C[F] = null.asInstanceOf[C[F]]): C[F] = a }
    final class optSearch3[C[_[-_, +_, +_]]] { def find[F[-_, +_, +_]](implicit a: C[F] = null.asInstanceOf[C[F]]): C[F] = a }

    assert(new optSearch2[IO2].find == null)
    assert(new optSearch3[IO3].find == null)

    assert(new optSearch2[Functor2].find == BioEither)
    assert(new optSearch2[Applicative2].find == BioEither)
    assert(new optSearch2[Monad2].find == BioEither)
    assert(new optSearch2[Bifunctor2].find == BioEither)
    assert(new optSearch2[Guarantee2].find == BioEither)
    assert(new optSearch2[ApplicativeError2].find == BioEither)
    assert(new optSearch2[Error2].find == BioEither)
    assert(new optSearch2[Bracket2].find == null)
    assert(new optSearch2[Panic2].find == null)
    assert(new optSearch2[Parallel2].find == null)
    assert(new optSearch2[IO2].find == null)
    assert(new optSearch2[Async2].find == null)
    assert(new optSearch2[Temporal2].find == null)
    assert(new optSearch2[Concurrent2].find == null)
    assert(new optSearch3[Ask3].find == null)
    assert(new optSearch3[MonadAsk3].find == null)
    assert(new optSearch3[Profunctor3].find == null)
    assert(new optSearch3[Arrow3].find == null)
    assert(new optSearch3[ArrowChoice3].find == null)
    assert(new optSearch3[Local3].find == null)

    assert(new optSearch2[Fork2].find == null)
    assert(new optSearch2[Primitives2].find == null)
    assert(new optSearch2[PrimitivesM2].find == null)
//    assert(new optSearch2[BlockingIO].find == null)  // hard to make searching this not require zio currently (`type ZIOWithBlocking` creates issue)
  }

}
