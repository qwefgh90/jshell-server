package clients

import java.io.PipedInputStream
import org.apache.commons.lang3.SystemUtils
import java.io.PipedOutputStream
import java.io.PrintStream
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
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
import java.util.concurrent.Executors
import actors.Messages._
import play.api.libs.json._

case class WebSocketClient(url: String, key: String)(implicit system: ActorSystem, materializer: Materializer) {
  def connect(): WebSocketJShell = {
    WebSocketJShell(url, key)
  }
}

case class WebSocketJShell(url: String, key: String)(implicit system: ActorSystem, materializer: Materializer)  {
  //implicit val system = ActorSystem()
  //implicit val materializer = ActorMaterializer()
  import system.dispatcher
    
  val helperConsoleMacOS = Array[Byte](27,91,50,53,59,57,82)
  val es = Executors.newCachedThreadPool();
  
  // make Sink with input stream
  val posFromServer = new PipedOutputStream()
  val pisFromServer = new PipedInputStream(1024 * 1024)	// 
  pisFromServer.connect(posFromServer)
  
    // mac os
  if(SystemUtils.IS_OS_MAC)
    posFromServer.write(helperConsoleMacOS)
  
  val wsSink: Sink[Message, Future[Done]] = Sink.foreach {
    case message: TextMessage.Strict => {
      //parse json
      print("recevied from ws: " + message.getStrictText)
      val jsResult = Json.parse(message.getStrictText).validate[InEvent]
      if(jsResult.isSuccess){
        val inEvent = jsResult.get
        if(inEvent.t == "p"){
          inEvent.m.getBytes().foreach(b => {
            if(b == '\n'){
              if(SystemUtils.IS_OS_MAC){
                posFromServer.write(Array[Byte](10))
                posFromServer.write(helperConsoleMacOS)
              }else
                posFromServer.write(Array[Byte](10))
            }else
              posFromServer.write(b)
          })
        }
      }else{
        println("error: " + jsResult.toString)
      }
    }
  }
  
  // make Source with output Stream
  val wsSource = StreamConverters.asOutputStream().map(bs =>
    TextMessage(Json.toJson(OutEvent("p", bs.utf8String)).toString))

  // make flow
  val flow = Flow.fromSinkAndSourceMat(wsSink, wsSource)(Keep.right)
  
  // materialized
  val (upgradeResponse, osToServer: OutputStream) =
      Http().singleWebSocketRequest(WebSocketRequest(url), flow)
  
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
  
  es.execute(new Runnable(){
    def run(){
      JavaShellToolBuilder.builder().in(pisFromServer, null).out(printStream).run()
    }  
  })
  
  def close() = {
    pisFromServer.close()
    printStream.close()
    system.terminate()
    es.shutdown()
  }
}