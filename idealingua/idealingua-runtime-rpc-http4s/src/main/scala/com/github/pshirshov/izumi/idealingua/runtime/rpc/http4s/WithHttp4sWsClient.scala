package com.github.pshirshov.izumi.idealingua.runtime.rpc.http4s

import java.net.URI
import java.util.concurrent.{ConcurrentHashMap, TimeUnit, TimeoutException}

import com.github.pshirshov.izumi.idealingua.runtime.rpc.{RPCPacketKind, _}
import io.circe.parser.parse
import io.circe.syntax._
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import scalaz.zio.{ExitResult, IO}

import scala.concurrent.duration.Duration

trait WithHttp4sWsClient {
  this: Http4sContext =>

  class ClientWsDispatcher(baseUri: URI, codec: IRTClientMultiplexor[BIO])
    extends IRTDispatcher with IRTResultZio with AutoCloseable {

    // TODO: no stale item cleanups
    protected val requests: ConcurrentHashMap[RpcPacketId, IRTMethodId] = new ConcurrentHashMap[RpcPacketId, IRTMethodId]()
    protected val responses: ConcurrentHashMap[RpcPacketId, IRTMuxResponse] = new ConcurrentHashMap[RpcPacketId, IRTMuxResponse]()

    protected val wsClient: WebSocketClient = new WebSocketClient(baseUri) {
      override def onOpen(handshakedata: ServerHandshake): Unit = {}

      override def onMessage(message: String): Unit = {
        logger.error(s"Incoming WS message: $message")

        val result = for {
          parsed <- IO.fromEither(parse(message))
          _ <- IO.sync(logger.info(s"parsed: $parsed"))
          decoded <- IO.fromEither(parsed.as[RpcResponse])
          method <- Option(requests.get(decoded.ref)) match {
            case Some(id) =>
              requests.remove(decoded.ref)
              IO.point(id)
            case None => IO.terminate(new IRTMissingHandlerException(s"No handler for ${decoded.ref}", decoded))
          }
          product <- codec.decode(decoded.data, method)
        } yield {
          (product, decoded)
        }

        ZIOR.unsafeRunSync(result) match {
          case ExitResult.Completed(v) =>
            responses.put(v._2.ref, v._1)

          case ExitResult.Failed(error, _) =>
            logger.error(s"Failed to process request: $error")


          case ExitResult.Terminated(causes) =>
            logger.error(s"Failed to process request, termination: $causes")
        }
      }

      override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
        logger.debug(s"WS connection closed: $code, $reason, $remote")
      }

      override def onError(exception: Exception): Unit = {
        logger.debug(s"WS connection errored: $exception")
      }
    }

    wsClient.connect()
    while (!wsClient.isOpen) {
      Thread.`yield`()
    }

    override def close(): Unit = {
      wsClient.closeBlocking()
    }

    protected val timeout = Duration.apply(2, TimeUnit.SECONDS)

    def dispatch(request: IRTMuxRequest): ZIO[Throwable, IRTMuxResponse] = {
      logger.trace(s"${request.method -> "method"}: Goint to perform $request")

      codec
        .encode(request)
        .flatMap {
          encoded =>
            val wrapped = RpcRequest(
              RPCPacketKind.RpcRequest,
              request.method.service.value,
              request.method.methodId.value,
              RpcPacketId.random(),
              encoded,
              Map.empty,
            )

            val out = transformRequest(wrapped).asJson.noSpaces
            requests.put(wrapped.id, request.method)
            wsClient.send(out)
            logger.debug(s"${request.method -> "method"}: Prepared request $encoded")


            IO.sync {
              val started = System.nanoTime()
              while (!responses.containsKey(wrapped.id) && Duration.fromNanos(System.nanoTime() - started) <= timeout) {
                Thread.sleep(10)
              }
            }.flatMap {
              _ =>
                Option(responses.get(wrapped.id)) match {
                  case Some(value) =>
                    logger.debug(s"Have response: $value")
                    IO.point(value)

                  case None =>
                    IO.terminate(new TimeoutException(s"No response for ${wrapped.id} in $timeout"))

                }
            }
        }
    }

    protected def transformRequest(request: RpcRequest): RpcRequest = request
  }

}
