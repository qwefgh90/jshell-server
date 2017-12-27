package example

import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import jdk.jshell.tool.JavaShellToolBuilder
import akka.stream.scaladsl.StreamConverters

import akka.{ Done, NotUsed }
import akka.http.scaladsl.Http
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._

import scala.concurrent.Future
import java.io.InputStream
import java.io.OutputStream

/*
 * 1. java application starts up in cotainer 
 * 2. connect to server
 * 3. run jshell
 * 4. read and write encoded string over akka stream
 * 5. exit jshell and stop the container 
 */
object Hello extends Greeting with App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  
  // make Sink with input stream
  val posFromServer = new PipedOutputStream()
  val pisFromServer = new PipedInputStream(posFromServer)
  
  val wsSink: Sink[Message, Future[Done]] = Sink.foreach {
    case message: TextMessage.Strict => {
      print(message.text)
      posFromServer.write(message.getStrictText.getBytes)
      if(message.text.contains("jshell> "))
        system.terminate()
    }
  }
  
  // make Source with output Stream
  val wsSource = StreamConverters.asOutputStream().map(bs => TextMessage(bs.utf8String))

  // make flow
  val flow = Flow.fromSinkAndSourceMat(wsSink, wsSource)(Keep.right)
  val addr = "ws://echo.websocket.org"
  
  // materialized
  val (upgradeResponse, osToServer: OutputStream) =
      Http().singleWebSocketRequest(WebSocketRequest(addr), flow)
  
  val printStream = new PrintStream(osToServer)
  
  val connected = upgradeResponse.map { upgrade =>
    // just like a regular http request we can access response status which is available via upgrade.response.status
    // status code 101 (Switching Protocols) indicates that server support WebSockets
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Done
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }
  connected.onComplete(println)
  
  JavaShellToolBuilder.builder().in(pisFromServer, null).out(printStream).run()
}

trait Greeting {
  lazy val greeting: String = "hello"
  
}
