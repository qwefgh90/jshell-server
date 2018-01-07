package websocket

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._
import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.scaladsl.StreamConverters
import scala.concurrent.Future
import akka.http.scaladsl.Http
import clients._
import actors.Messages._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json._
import scala.concurrent.ExecutionContext
import play.api.inject.guice.GuiceApplicationBuilder
import controllers.TestSidHandler
import controllers.SidHandler
import play.api.inject.bind
import actors.JShellLauncher

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class IntegrationSpec extends PlaySpec {
  
  val testSid = "123"
  val appBuilder = GuiceApplicationBuilder()
    .overrides(bind[SidHandler].to(new TestSidHandler(testSid)))
  val jshellLatency = 5000
  
  def connect[T](path: String, port: String, sink: Sink[Message, _], source: Source[Message, T])(implicit system: ActorSystem, mat: Materializer) :T = {
    val flow = Flow.fromSinkAndSourceMat(sink, source)(Keep.right)
    val addr = s"ws://localhost:${port}/${path}"
    
    // materialized
    val (upgradeResponse, mv) =
        Http(system).singleWebSocketRequest(WebSocketRequest(addr), flow)
        
    val connected = upgradeResponse.map { upgrade =>
        // just like a regular http request we can access response status which is available via upgrade.response.status
        // status code 101 (Switching Protocols) indicates that server support WebSockets
        if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Done
      }else {
         throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }
    connected.onComplete(println)
    mv
  }
    
  "Web socket actors test for graceful termination with /exit" in new WithServer(appBuilder.build()) {
    System.setProperty("http.port", port.toString)
    val system = this.app.injector.instanceOf[ActorSystem]
    val ec = this.app.injector.instanceOf[ExecutionContext]
    var welcomeMsg = ""
    
    val printSink: Sink[Message, Future[Done]] = Sink.foreach {
      case message: TextMessage.Strict => {
        val oe = Json.parse(message.getStrictText).validate[OutEvent].get
        val msg = oe.m
        if(oe.t == MessageType.o.toString)
          welcomeMsg += msg
      }
    }
    
    val wsSource = StreamConverters.asOutputStream().map(bs => TextMessage(bs.utf8String))
    
    await{
      Future{
        val outputStream = connect("clientws", port.toString, printSink, wsSource)(system, implicitMaterializer)
        Thread.sleep(jshellLatency)
        outputStream.write(Json.toJson(InEvent(MessageType.i.toString, "/exit\n")).toString.getBytes)
        Thread.sleep(jshellLatency)
      }
    }
    
    assert(welcomeMsg.contains("""|  Welcome to JShell -- Version 9.0.1"""))
    assert(welcomeMsg.contains("""|  For an introduction type: /help intro"""))
    assert(welcomeMsg.contains("""jshell> """))
    assert(welcomeMsg.contains("""|  Goodbye"""))
  }
  
  "Web socket actors test for graceful termination with closing client connection" in new WithServer(appBuilder.build()) {
    System.setProperty("http.port", port.toString)
    val system = this.app.injector.instanceOf[ActorSystem]
    val ec = this.app.injector.instanceOf[ExecutionContext]
    var welcomeMsg = ""
    
    val printSink: Sink[Message, Future[Done]] = Sink.foreach {
      case message: TextMessage.Strict => {
        val oe = Json.parse(message.getStrictText).validate[OutEvent].get
        val msg = oe.m
        if(oe.t == MessageType.o.toString)
          welcomeMsg += msg
      }
    }
    
    val wsSource = StreamConverters.asOutputStream().map(bs => TextMessage(bs.utf8String))
    
    await{
      Future{
        val outputStream = connect("clientws", port.toString, printSink, wsSource)(system, implicitMaterializer)
        Thread.sleep(jshellLatency)
        outputStream.close()
        Thread.sleep(jshellLatency)
      }
    }
    
    assert(welcomeMsg.contains("""|  Welcome to JShell -- Version 9.0.1"""))
    assert(welcomeMsg.contains("""|  For an introduction type: /help intro"""))
    assert(welcomeMsg.contains("""jshell> """))
  }
}
