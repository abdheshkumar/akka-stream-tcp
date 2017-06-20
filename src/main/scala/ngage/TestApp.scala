package ngage

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Framing, Source, Tcp}
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.util.ByteString

import scala.concurrent.Future

/**
  * Created by abdhesh on 19/06/17.
  */
object TestApp extends App {
  implicit val system = ActorSystem("test-app")
  implicit val actorMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  val connections: Source[IncomingConnection, Future[ServerBinding]] =
    Tcp().bind("127.0.0.1", 8888)

  connections runForeach { connection =>
    println(s"New connection from: ${connection.remoteAddress}")

    val echo = Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
      .map(_.utf8String)
      .map(_ + "!!!\n")
      .map(ByteString(_))

    val aa = connection.handleWith(echo)
    println("::::"+aa)
  }

}
