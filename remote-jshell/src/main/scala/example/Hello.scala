package example

import java.io.PipedInputStream
import org.apache.commons.lang3.SystemUtils
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
import java.util.concurrent.Executors

/*
 * 1. java application starts up in cotainer 
 * 2. connect to relay server
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
  val psFromServer = new PrintStream(posFromServer)
  val pisFromServer = new PipedInputStream(1024 * 1024)	// 
  pisFromServer.connect(posFromServer)
  
  // mac os
  if(SystemUtils.IS_OS_MAC)
    posFromServer.write(Array[Byte](27,91,50,53,59,57,82))
  
  val wsSink: Sink[Message, Future[Done]] = Sink.foreach {
    case message: TextMessage.Strict => {
      //echo
      //print("@"+message.getStrictText)
      if(message.getStrictText.contains("Welcome")){
        psFromServer.print("int i = 0;")
        if(SystemUtils.IS_OS_MAC)
          posFromServer.write(Array[Byte](10,27,91,50,53,59,57,82))
        else
          posFromServer.write(Array[Byte](10))
      }
      else if(message.getStrictText.contains("i ==> 0")){
        psFromServer.print("int i = 1;")
        if(SystemUtils.IS_OS_MAC)
          posFromServer.write(Array[Byte](10,27,91,50,53,59,57,82)) // for reading each byte immediately
        else
        	posFromServer.write(Array[Byte](10))
      }
    }
  }
  
  val es = Executors.newCachedThreadPool();
  
  /*es.submit(new Runnable(){
    def run(){
      var f = false
      while(!f){
        val r = System.in.read()
        //System.out.print("b:"+r)
        if(r == 'q')
          f = true
          
      }
    }
  })*/
  
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
  java.lang.Thread.sleep(10000);
  System.out.println("terminated!!")
  pisFromServer.close()
  printStream.close()
  system.terminate()
  es.shutdown()
}

trait Greeting {
  lazy val greeting: String = "hello"
  
}
