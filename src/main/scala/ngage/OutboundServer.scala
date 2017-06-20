package ngage

import java.net.{InetAddress, InetSocketAddress}
import java.util.UUID

import akka.{Done, NotUsed}
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.Tcp.IncomingConnection
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.ByteString
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Created by abdhesh on 16/06/17.
  */
object OutboundServer {
  val address = "freeswitch.outbound.address"
  val port = "freeswitch.outbound.port"

  def apply(config: Config)(implicit system: ActorSystem, materializer: ActorMaterializer): OutboundServer =
    new OutboundServer(config)

  def apply(address: String, port: Int)(implicit system: ActorSystem, materializer: ActorMaterializer): OutboundServer =
    new OutboundServer(address, port)

  def apply()(implicit system: ActorSystem, materializer: ActorMaterializer): OutboundServer =
    new OutboundServer(address = "localhost", port = 1234)

}

class OutboundServer(address: String, port: Int)(implicit system: ActorSystem, materializer: ActorMaterializer) {

  def this(config: Config)(implicit system: ActorSystem, materializer: ActorMaterializer) =
    this(config.getString(OutboundServer.address), config.getInt(OutboundServer.port))

  def startWith(fun: Connection => Unit): Unit = fun(Connection(Tcp().bind(address, port)))

  def startWith1(fun: TcpConnection => Flow[ByteString, ByteString, NotUsed]): Future[Done] = {
    Tcp().bind(address, port).runForeach {
      connection =>
        println(s"New connection from: ${connection.remoteAddress}")
        connection.handleWith(fun(TcpConnection(connection.remoteAddress)))
    }
  }


  case class TcpConnection(remoteAddress: InetSocketAddress) {
    println("::::TcpConnection::")
    var data = ""

    def handler(fun: FreeSwitchMessage => Unit) = Flow[ByteString].map {
      f =>
        data = data + f.utf8String
        println(s":::Data::${data}:: connection::${remoteAddress}")
        val message = FreeSwitchMessage(Map.empty) // Parse byte string into Freeswitch message
        fun(message)
        f
    }

    def handler(consumer: ActorRef, sendOnComplete: Any): Flow[ByteString, ByteString, NotUsed] = {
      val in = Flow[ByteString].map(f => FreeSwitchMessage(Map.empty)).to(Sink.actorRef(consumer, sendOnComplete))
      val out = Source.actorRef[ByteString](1024, OverflowStrategy.fail).mapMaterializedValue(consumer ! _)
      Flow.fromSinkAndSource(in, out)
    }

  }

  case class Connection(server: Source[IncomingConnection, Future[Tcp.ServerBinding]]) {

    def handler(fun: FreeSwitchMessage => Unit) = {
      val flow = Flow[ByteString].map {
        f =>
          val message = FreeSwitchMessage(Map.empty) // Parse byte string into Freeswitch message
          fun(message)
          f
      }

      server.runForeach {
        tcpConnection =>
          tcpConnection handleWith (flow)
      }
    }

    def handler(consumer: ActorRef, sendOnComplete: Any) = {
      val in = Flow[ByteString].map(f => FreeSwitchMessage(Map.empty)).to(Sink.actorRef(consumer, sendOnComplete))
      val out = Source.actorRef[ByteString](1024, OverflowStrategy.fail).mapMaterializedValue(consumer ! _)
      val flow = Flow.fromSinkAndSource(in, out)
      server.runForeach {
        tcpConnection =>
          tcpConnection.handleWith(flow)
      }
    }

    def handler(flow: Flow[ByteString, ByteString, NotUsed]) = server.runForeach {
      tcpConnection =>
        tcpConnection handleWith (flow)
    }
  }


}

object Test extends App {
  implicit val system = ActorSystem("test-system")
  implicit val actorMaterializer = ActorMaterializer()
  /*OutboundServer("", -999).startWith {
    connection => {
      connection.handler {
        message =>
          message.headers
        //process message
      }
    }
  }

  OutboundServer("", -999).startWith {
    connection =>
      connection.handler(Flow[ByteString].map(f => f))
  }

  OutboundServer("", -999).startWith {
    connection =>
      connection.handler(ActorRef.noSender, "sendOnComplete")
  }

  OutboundServer("", -999).startWith1 {
    tcpConnection =>
      tcpConnection.handler(ActorRef.noSender, "sendOnComplete")
  }*/

  OutboundServer("localhost", 8080).startWith1 {
    tcpConnection => {
      tcpConnection.handler {
        message =>
          message.headers
        //process message
      }
    }
  }


}

case class ParsedMessage(byteString: ByteString)

case class FreeSwitchMessage(headers: Map[String, String])
