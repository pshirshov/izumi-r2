package izumi.distage.provisioning.strategies.cglib

import izumi.distage.model.provisioning.proxies.ProxyProvider
import izumi.distage.model.provisioning.proxies.ProxyProvider.ProxyParams.{Empty, Params}
import izumi.distage.model.provisioning.proxies.ProxyProvider.{DeferredInit, ProxyContext}
import izumi.distage.model.reflection.DIKey
import izumi.distage.provisioning.strategies.cglib.exceptions.CgLibInstantiationOpException
import izumi.fundamentals.platform.exceptions.IzThrowable._
import net.sf.cglib.proxy.{Callback, Enhancer}

class CglibProxyProvider extends ProxyProvider {

  override def makeCycleProxy(deferredKey: DIKey, proxyContext: ProxyContext): DeferredInit = {
    val nullDispatcher = new CglibNullMethodInterceptor(deferredKey)
    val nullProxy = mkDynamic(nullDispatcher, proxyContext)

    val realDispatcher = new CglibAtomicRefDispatcher(nullProxy)
    val realProxy = mkDynamic(realDispatcher, proxyContext)

    DeferredInit(realDispatcher, realProxy)
  }

  private def mkDynamic(dispatcher: Callback, proxyContext: ProxyContext): AnyRef = {
    val clazz = proxyContext.runtimeClass

    // Enhancer.setSuperclass is side-effectful, so we had to copypaste
    val enhancer = new Enhancer()

    if (clazz.isInterface) {
      enhancer.setInterfaces(Array[Class[?]](clazz, classOf[DistageProxy]))
    } else if (clazz == classOf[Any]) {
      enhancer.setInterfaces(Array(classOf[DistageProxy]))
    } else {
      enhancer.setSuperclass(clazz)
      enhancer.setInterfaces(Array(classOf[DistageProxy]))
    }

    enhancer.setCallback(dispatcher)

    try {
      proxyContext.params match {
        case Empty =>
          enhancer.create()
        case Params(types, values) =>
          enhancer.create(types, values.asInstanceOf[Array[Object]])
      }
    } catch {
      case f: Throwable =>
        throw new CgLibInstantiationOpException(
          s"Failed to instantiate class with CGLib, make sure you don't dereference proxied parameters in constructors: " +
          s"class=${proxyContext.runtimeClass}, params=${proxyContext.params}, exception=${f.stackTrace}",
          clazz,
          proxyContext.params,
          proxyContext.op,
          f,
        )
    }
  }
}
