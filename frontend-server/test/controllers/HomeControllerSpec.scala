package controllers

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

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class HomeControllerSpec extends PlaySpec {
  
  def connect(path: String, port: String, sink: Sink[Message, _], source: Source[Message, _])(implicit system: ActorSystem, mat: Materializer) = {
    val flow = Flow.fromSinkAndSourceMat(sink, source)(Keep.right)
    val addr = s"ws://localhost:${port}/${path}"
    
    // materialized
    val (upgradeResponse, _) =
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
  }
  
  
  "test server logic" in new WithServer() {
    val system = this.app.injector.instanceOf[ActorSystem]
    var welcomeMsg = ""
    
    val printSink: Sink[Message, Future[Done]] = Sink.foreach {
      case message: TextMessage.Strict => {
        val oe = Json.parse(message.getStrictText).validate[OutEvent].get
        val msg = oe.m
        welcomeMsg += msg
      }
    }
    
    val wsSource = StreamConverters.asOutputStream().map(bs => TextMessage(bs.utf8String))
    
    await{
      Future{
        connect("clientws", port.toString, printSink, wsSource)(system, implicitMaterializer)
        val wsJshell = WebSocketClient(s"ws://localhost:${port}/shellws", "123")(system, implicitMaterializer).connect()
        Thread.sleep(5000)
      }
    }
    
    assert(welcomeMsg.contains("""|  Welcome to JShell -- Version 9.0.1"""))
    assert(welcomeMsg.contains("""|  For an introduction type: /help intro"""))
    assert(welcomeMsg.contains("""jshell> """))
  }
  
  
  "HomeController GET" should {
/*
    "render the index page from a new instance of controller" in {
      val controller = new HomeController(stubControllerComponents())
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to Play")
    }

    "render the index page from the application" in {
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to Play")
    }

    "render the index page from the router" in {
      val request = FakeRequest(GET, "/")
      val home = route(app, request).get

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Welcome to Play")
    }
    */
  }
}
