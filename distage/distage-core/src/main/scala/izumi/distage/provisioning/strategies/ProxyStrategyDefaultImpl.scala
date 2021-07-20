package izumi.distage.provisioning.strategies

import izumi.distage.model.effect.QuasiIO
import izumi.distage.model.effect.QuasiIO.syntax._
import izumi.distage.model.exceptions._
import izumi.distage.model.plan.ExecutableOp.{CreateSet, MonadicOp, ProxyOp, WiringOp}
import izumi.distage.model.provisioning.proxies.ProxyDispatcher.ByNameDispatcher
import izumi.distage.model.provisioning.proxies.ProxyProvider.DeferredInit
import izumi.distage.model.provisioning.proxies.{ProxyDispatcher, ProxyProvider}
import izumi.distage.model.provisioning.strategies._
import izumi.distage.model.provisioning.{NewObjectOp, OperationExecutor, ProvisioningKeyProvider, WiringExecutor}
import izumi.distage.model.reflection.MirrorProvider
import izumi.distage.model.reflection._
import izumi.distage.provisioning.strategies.ProxyStrategyDefaultImpl.FakeSet
import izumi.fundamentals.platform.language.unused
import izumi.reflect.TagK

/**
  * Limitations:
  *  - Will not work for any class which performs any operations on forwarding refs within constructor
  *  - Untested on constructors accepting primitive values, will fail most likely
  */
class ProxyStrategyDefaultImpl(
                                proxyProvider: ProxyProvider,
                                mirrorProvider: MirrorProvider,
                              ) extends ProxyStrategyDefaultImplPlatformSpecific(proxyProvider, mirrorProvider)
  with ProxyStrategy {

  override def makeProxy(context: ProvisioningKeyProvider, @unused executor: WiringExecutor, makeProxy: ProxyOp.MakeProxy): Seq[NewObjectOp] = {
    val cogenNotRequired = makeProxy.byNameAllowed

    val proxyInstance = if (cogenNotRequired) {
      val proxy = new ByNameDispatcher(makeProxy.target)
      DeferredInit(proxy, proxy)
    } else {
      val tpe = proxyTargetType(makeProxy)
      if (!mirrorProvider.canBeProxied(tpe)) {
        failCogenProxy(tpe, makeProxy)
      }
      makeCogenProxy(context, tpe, makeProxy)
    }

    Seq(
      NewObjectOp.NewInstance(makeProxy.target, proxyInstance.proxy),
      NewObjectOp.NewInstance(proxyControllerKey(makeProxy.target), proxyInstance.dispatcher),
    )
  }

  override def initProxy[F[_] : TagK](
                                       context: ProvisioningKeyProvider,
                                       executor: OperationExecutor,
                                       initProxy: ProxyOp.InitProxy,
                                     )(implicit F: QuasiIO[F]
                                     ): F[Seq[NewObjectOp]] = {
    val target = initProxy.proxy.target
    val key = proxyControllerKey(target)

    context.fetchUnsafe(key) match {
      case Some(dispatcher: ProxyDispatcher) =>
        executor
          .execute(context, initProxy.proxy.op)
          .flatMap(_.toList match {
            case NewObjectOp.NewInstance(_, instance) :: Nil =>
              F.maybeSuspend(dispatcher.init(instance.asInstanceOf[AnyRef]))
                .map(_ => Seq(
                  NewObjectOp.NewInstance(initProxy.target, instance)
                ))

            case (r@NewObjectOp.NewResource(_, instance, _)) :: Nil =>
              val finalizer = r.asInstanceOf[NewObjectOp.NewResource[F]].finalizer
              F.maybeSuspend(dispatcher.init(instance.asInstanceOf[AnyRef]))
                .map(_ => Seq(
                  NewObjectOp.NewInstance(initProxy.target, instance),
                  NewObjectOp.NewFinalizer(target, finalizer)
                ))

            case r =>
              throw new UnexpectedProvisionResultException(s"Unexpected operation result for $key: $r, expected a single NewInstance!", r)
          })
      case _ =>
        throw new MissingProxyAdapterException(s"Cannot get dispatcher $key for $initProxy", key, initProxy)
    }
  }

  protected def proxyTargetType(makeProxy: ProxyOp.MakeProxy): SafeType = {
    makeProxy.op match {
      case _: CreateSet =>
        // CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
        SafeType.get[FakeSet[?]]
      case op: WiringOp.CallProvider =>
        op.target.tpe
      case op: MonadicOp.AllocateResource =>
        op.target.tpe
      case op: MonadicOp.ExecuteEffect =>
        op.target.tpe
      case op: WiringOp.UseInstance =>
        throw new UnsupportedOpException(s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op", op)
      case op: WiringOp.ReferenceKey =>
        throw new UnsupportedOpException(s"Tried to execute nonsensical operation - shouldn't create proxies for references: $op", op)
    }
  }

  protected def proxyControllerKey(m: DIKey): DIKey = {
    DIKey.ProxyControllerKey(m, SafeType.get[ProxyDispatcher])
  }

}

object ProxyStrategyDefaultImpl {
  // CGLIB-CLASSLOADER: when we work under sbt cglib fails to instantiate set
  private trait FakeSet[A] extends Set[A]
}
