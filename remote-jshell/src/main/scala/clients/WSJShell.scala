package clients

import java.io.PipedInputStream
import org.apache.commons.lang3.SystemUtils
import java.io.PipedOutputStream
import java.io.PrintStream
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import jdk.jshell.tool.JavaShellToolBuilder
import jdk.jshell.execution.LocalExecutionControlProvider
import akka.stream.scaladsl.StreamConverters
import akka.{ Done, NotUsed }
import akka.http.scaladsl.Http
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import scala.concurrent.{Future, blocking}
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import actors.Messages._
import play.api.libs.json._
import scala.util.{Success, Failure}
import com.typesafe.scalalogging.Logger
import scala.concurrent.ExecutionContext
import jdk.jshell.spi._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.RawHeader
import clients.SidHeader
import akka.http.scaladsl.model.headers.BasicHttpCredentials

case class WebSocketClient(url: String, sid: String)(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext) {
  def connect(): WSJShell = {
    WSJShell(url, sid)
  }
}

case class WSJShell(url: String, sid: String)(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext)  {
  val logger = Logger(classOf[WSJShell])
  
  // make Sink with input stream
  val posFromServer = new PipedOutputStream()
  val pisFromServer = new PipedInputStream(1024 * 1024) 
  pisFromServer.connect(posFromServer)
  
  // mac os
  if(SystemUtils.IS_OS_MAC)
    posFromServer.write(getNewLine)
  
  val wsSink: Sink[Message, Future[Done]] = Sink.foreach {
    case message: TextMessage.Strict => {
      val jsResult = Json.parse(message.getStrictText).validate[InEvent]
      if(jsResult.isSuccess){
        val inEvent = jsResult.get
        if(inEvent.t == MessageType.ic.toString && inEvent.m == InternalControlValue.terminate.toString){
          logger.info("Requested to close: {}", inEvent.toString)
        	posFromServer.write("/exit".getBytes)
          posFromServer.write(getNewLine)
        }else if(inEvent.t == MessageType.i.toString){
          val msg = inEvent.m
          msg.getBytes().foreach(b => {
            if(b == '\n')
              posFromServer.write(getNewLine)
            else
              posFromServer.write(b)
          })
        }
      }else
        logger.error(jsResult.toString)
    }
  }
  
  // make Source with output Stream
  val wsSource = StreamConverters.asOutputStream().map(bs => {
    logger.info(s"out: ${bs.toString}")
    TextMessage(Json.toJson(OutEvent(MessageType.o.toString, bs.utf8String)).toString)
  })
  
  // make flow
  val flow = Flow.fromSinkAndSourceMat(wsSink, wsSource)(Keep.right)
  
  // materialized
  val (upgradeResponse, osToServer: OutputStream) =
      Http().singleWebSocketRequest(WebSocketRequest(url, extraHeaders = scala.collection.immutable.Seq(Authorization(BasicHttpCredentials("sid",sid)))), flow)
  
  val printStream = new PrintStream(osToServer)
  val connected = upgradeResponse.map { upgrade =>
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      upgrade.response.toString
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }
  
  connected.onComplete({
    case Success(v) => {
        logger.info(s"Success to connection ${v}")
        newJShell()
      }
      case Failure(e) => {
        logger.error("Failed to connection", e)
        close()
      }
    })
  private def newJShell(){
    var closeState = false;
	  Future{
	    blocking{
  	    val list = java.util.ServiceLoader.load(classOf[jdk.jshell.spi.ExecutionControlProvider], ClassLoader.getSystemClassLoader)
  	    closeState = true;
  	    list.forEach(e => logger.info("name: " + e.name()))
  	    Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader)
  	    JavaShellToolBuilder.builder().in(pisFromServer, null).out(printStream).run()
  		  close()
	    }
	  }
	  Future{
	    blocking{
	    	while(!closeState) {
	    		if(printStream.checkError()){
	    			logger.info("A error occurs in PrintStream. JShell will be terminated")
	    			posFromServer.write("/exit".getBytes)
	    			posFromServer.write(getNewLine)
	    			closeState = true
	    		}
	    		Thread.sleep(1000)
	    	}
	    }
	  }
  }
    
  private def getNewLine = { 
    val helperConsoleMacOS = Array[Byte](27,91,50,53,59,57,82)  
    if(SystemUtils.IS_OS_MAC){
    	Array[Byte](10) ++ helperConsoleMacOS
    }else
    	Array[Byte](10)
  }
  
  protected def close() = {
    logger.info("Cleaning stream resources and executor")
    pisFromServer.close()
    printStream.close()
  }
}