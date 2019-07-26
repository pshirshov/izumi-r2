package com.github.pshirshov.izumi.distage.impl

import com.github.pshirshov.izumi.fundamentals.reflection.LTT
import com.github.pshirshov.izumi.fundamentals.reflection._

import org.scalatest.WordSpec

class LightTypeTagTest extends WordSpec {
  type F[T] = T
  type FP[+T] = List[T]
  type L[P] = List[P]
  type LN[P <: Number] = List[P]
  trait T1[U[_]]

  trait T2[U[_[_], _[_]]]

  trait T0[A[_], B[_]]
  //  type K = T1[F]
//  val a: K = new T1[F] {}
  trait C {
    type A
  }

  trait R[K, A <: R[K, A]]
  trait R1[K] extends R[K, R1[K]]
  type S[A, B] = Either[B, A]

  trait W1
  trait W2
  trait W3[_]

  trait I1
  trait I2 extends I1


  def  println(o: Any) = info(o.toString)
  def  println(o: FLTT) = info(o.t.t.toString)
  "lightweight type tags" should {
    "xxx" in {
//      println(`LTT[_]`[R1])
//      println(LTT[Int])
//      println(LTT[List[Int]])
//      println(LTT[F[Int]])
//      println(LTT[FP[Int]])
//
//      println(`LTT[+_]`[FP])
//      println(`LTT[_]`[L])
//      //println(`LTT[_]`[LN])
//
//      println(`LTT[A, _ <: A]`[Integer, LN])
//      println(`LTT[_]`[Either[Unit, ?]])
//      println("lambda:")
//      println(`LTT[_]`[S[Unit, ?]])
      println("F")
      println(`LTT[_[_]]`[T1])
      println(`LTT[_]`[F])
      println(LTT[T1[F]])

      println("FP")
      println(`LTT[_[_]]`[T1])
      println(`LTT[_]`[FP])
      println(LTT[T1[FP]])

      println("E")
      println(`LTT[_[_]]`[T0[F, ?[_]]])
      //println(`LTT[_]`[Either[Unit, ?]])
      println(LTT[T0[F, FP]])
      println(LTT[T2[T0]])

//      println("List")
//      println(`LTT[_]`[List])
//      println(LTT[T1[List]])

    }

    "support subtype checks" in {
//      assert(LTT[Int].t <:< LTT[AnyVal].t)
//      assert(LTT[List[Int]].t <:< `LTT[_]`[List].t)
//      println(LTT[List[I2]])

      //println(LTT[List[I1]])
      //assert(LTT[List[I2]].t <:< LTT[List[I1]].t)
    }

//    "support structural types" in {
//
//      println(LTT[{def a: Int}])
//
//    }
//
//    "support PDTs" in {
//      val a: C = new C {
//        override type A = Int
//      }
//
//      println(LTT[a.A])
//    }
//
//    "support refinements" in {
//      println(LTT[W1 with W2])
//      type T[A] = W3[A] with W2
//      println(`LTT[_]`[T])
//    }

//    "resolve concrete types through PDTs and projections" in {
//      val a1 = new C {
//        override type A = Int
//      }
//      type X = {type A = Int}
//
//      assert(LTT[a1.A] == LTT[X#A])
//
//
//    }
  }
}
