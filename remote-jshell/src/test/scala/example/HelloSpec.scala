package example

import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream
import java.util.concurrent.Executors

import scala.concurrent.Future

import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually._

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.StreamConverters
import jdk.jshell.tool.JavaShellToolBuilder
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.apache.commons.lang3.SystemUtils

class HelloSpec extends FlatSpec with Matchers {
  "The Hello object" should "say hello" in {
    Hello.greeting shouldEqual "hello"
  }
  "JShell" should "connect to echo server" in {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    import system.dispatcher
    
    // make Sink with input stream
    val posFromServer = new PipedOutputStream()
    val psFromServer = new PrintStream(posFromServer)
    val pisFromServer = new PipedInputStream(1024 * 1024)
    pisFromServer.connect(posFromServer)
    
    // OS X for reading each byte immediately
    if(SystemUtils.IS_OS_MAC)
    	posFromServer.write(Array[Byte](27,91,50,53,59,57,82))

    	var expectedPrint = false
    	val wsSink: Sink[Message, Future[Done]] = Sink.foreach {
    	case message: TextMessage.Strict => {
    		//echo
    		print(message.getStrictText)
    		if(message.getStrictText.contains("Welcome")){
    			psFromServer.print("int i = 0;")
    			if(SystemUtils.IS_OS_MAC)
    				posFromServer.write(Array[Byte](27,91,50,53,59,57,82))
          else
          	posFromServer.write(Array[Byte](10))
    		}
    		else if(message.getStrictText.contains("i ==> 0")){
    			psFromServer.print("int i = 1;")
    			if(SystemUtils.IS_OS_MAC)
    				posFromServer.write(Array[Byte](27,91,50,53,59,57,82))
      		else
    				posFromServer.write(Array[Byte](10))

    		}else if(message.getStrictText.contains("i ==> 1"))
    			expectedPrint = true
    	}
    }
    
    val es = Executors.newCachedThreadPool();
    
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
    
    es.submit(new Runnable(){
      def run(){
        JavaShellToolBuilder.builder().in(pisFromServer, null).out(printStream).run()
      }  
    })

    try{
      eventually (timeout(Span(10, Seconds))){ Thread.sleep(50); expectedPrint should be (true) }
    }finally{
      psFromServer.print("/exit")
      posFromServer.write(Array[Byte](10,27,91,50,53,59,57,82))
      pisFromServer.close()
      printStream.close()
      system.terminate()
      es.shutdown()
    }
  }
}
