package clients

import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.blocking
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

import org.apache.commons.lang3.SystemUtils

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger

import actors.Messages.InEvent
import actors.Messages.InternalControlValue
import actors.Messages.MessageType
import actors.Messages.OutEvent
import actors.Messages.inEventFormat
import actors.Messages.outEventFormat
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.StreamConverters
import akka.util.Timeout
import clients.delegate.JavaBinaryOnwerDelegateRunner
import jdk.jshell.spi.ExecutionControlProvider
import jdk.jshell.tool.JavaShellToolBuilder
import play.api.libs.json.Json
import net.sourceforge.prograde.sm._
import clients.security.JShellSecurityManager
import java.util.concurrent.Executors
import java.util.prefs.Preferences

case class WebSocketClient(url: String, sid: String)(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext, config: Config) {
  def connect(): WSJShell = {
    WSJShell(url, sid)
  }
}

case class WSJShell(url: String, sid: String)(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext, config: Config)  {
  import clients.delegate.JavaBinaryOnwerDelegateRunner._
  val logger = Logger(classOf[WSJShell])
  val singleEc = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor)
  val promise = Promise[Int]()
  val selectionFuture = system.actorSelection("/user/delegate").resolveOne(10 seconds)
  val delegateActor = Await.result(selectionFuture.recover{
    case th => {
      logger.info("We will create new delegateActor", th)
      system.actorOf(JavaBinaryOnwerDelegateRunner.props(), name="delegate")
  }}, Duration(10, TimeUnit.SECONDS))
  
  val bufferSize = config.getInt("client.buffer-size")
  val throttlePer = config.getInt("client.throttle-millisconds")
  val mode = config.getString("client.mode")
  val customPath = config.getString("client.java-home")
  val xmx = config.getString("client.shell.jvm.xmx")
  var cleaned = false;
  var closed = false;
  if(customPath != ""){
	  logger.info("new java home: " + customPath)
	  System.setProperty("java.home", customPath) // change java home
  }else
	  logger.info("not found new home")
  logger.debug(s"try to connect to url: ${url}, sid: ${sid}")
  
  // make Sink with input stream
  val posFromServer = new PipedOutputStream()
  val pisFromServer = new PipedInputStream(1024 * 1024)
  pisFromServer.connect(posFromServer)
  
  // mac os
  if(SystemUtils.IS_OS_MAC)
    posFromServer.write(getNewLine)
  
  val wsSink: Sink[Message, NotUsed] = Flow[Message]
  .buffer(bufferSize, OverflowStrategy.fail)
  .throttle(1, FiniteDuration(throttlePer,TimeUnit.MILLISECONDS), 0, ThrottleMode.shaping)
  .recover{
    case th: Throwable =>
      logger.error("Client source is too much fast", th)
      posFromServer.write(getNewLine)
      posFromServer.write("/exit".getBytes)
      posFromServer.write(getNewLine)
  }
  .to(Sink.foreach {
    case message: TextMessage.Strict => {
      val jsResult = Json.parse(message.getStrictText).validate[InEvent]
      if(jsResult.isSuccess){
        val inEvent = jsResult.get
        if(inEvent.t == MessageType.ic.toString && inEvent.m == InternalControlValue.terminate.toString){
          logger.info("Requested to close: {}", inEvent.toString)
          posFromServer.write(getNewLine)
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
        posFromServer.flush()
      }else
        logger.error(jsResult.toString)
    }
  })
  
  // make Source with output Stream
  val wsSource = StreamConverters.asOutputStream().map(bs => {
    implicit val timeout = Timeout(20 seconds) 
    logger.debug(s"shell out(${Charset.defaultCharset().toString}): ${bs.decodeString(Charset.defaultCharset().toString)}")
    if(cleaned == false){
    	val future = delegateActor ? Cleaning()
    	future.onComplete((result) => {
    	  result.recover{
    	    case ex: Exception => logger.error("cleaning error", ex)
    	  }
    	})
    	cleaned = true
    }
    TextMessage(Json.toJson(OutEvent(MessageType.o.toString, bs.decodeString(Charset.defaultCharset().toString))).toString)
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
      throw new RuntimeException(s"Connection failed	: ${upgrade.response.status}")
    }
  }
  
  connected.onComplete({
    case Success(v) => {
        logger.info(s"Success to connection ${v}")
        newJShell()
      }
      case Failure(e) => {
        logger.error("Failed to connection", e)
        close(None)
      }
    })
    
  private def newJShell(){
    implicit val timeout = Timeout(20 seconds) 
    logger.info("New jshell started.")
  	val future = delegateActor ? Delegate("jshell_", Paths.get(customPath))
  	future.onComplete((newIdTry) => {
  	  newIdTry.map{newId =>
  	    if(newId != "")
    	    logger.info(s"$newId is create.")
    	  Future{
  	      blocking{
  	        if(System.getSecurityManager != null){
  	          System.getSecurityManager.asInstanceOf[JShellSecurityManager].enable()
  	          System.getSecurityManager.asInstanceOf[JShellSecurityManager].setAllowPaths(List(Paths.get(s"/home/${newIdTry}")))
  	        }
            val list = java.util.ServiceLoader.load(classOf[jdk.jshell.spi.ExecutionControlProvider], ClassLoader.getSystemClassLoader)
            list.forEach(e => logger.info("name: " + e.name()))
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader)
            val pref = Preferences.userRoot().node(newId.toString)
            
            JavaShellToolBuilder.builder().in(pisFromServer, null).out(printStream).persistence(pref)
                .run(s"-R -Xmx${xmx}")
            close(Some(newId.toString))
  	      }
  	    }(singleEc).recover{case ex: Exception => {
    	      logger.error("starting jshell is failed", ex)
    	      close(Some(newId.toString))
    	    }
    	  }
    	  Future{
    	    blocking{
    	    	while(!closed) {
    	    		if(printStream.checkError()){
    	    			logger.info("A error occurs in PrintStream. JShell will be terminated")
    	    			posFromServer.write("/exit".getBytes)
    	    			posFromServer.write(getNewLine)
    	    		}
    	    		Thread.sleep(2000)
    	    	}
    	    	//terminate eventually
    	    	Thread.sleep(10000)
            close(Some(newId.toString))
    	    }
    	  }
  	  }.recover{case ex: Exception => {
    	    logger.error("delegating is failed", ex)
    	    close(None)
          closed = true
    	  }
    	}
  	})
  }
  
  def future = {
    promise.future
  }
  
  private def getNewLine = {
    val helperConsoleMacOS = Array[Byte](27,91,50,53,59,57,82)  
    if(SystemUtils.IS_OS_MAC){
    	Array[Byte](10) ++ helperConsoleMacOS
    }else
    	Array[Byte](10)
  }
  
  protected def close(idOpt: Option[String]) = {
    if(closed == false){
      closed = true
      idOpt.map(id => delegateActor ! DeleteUser(id))
      pisFromServer.close()
      printStream.close()
      singleEc.awaitTermination(10, TimeUnit.SECONDS)
      promise.success(0)
      logger.info("Cleaning stream resources and executor")
    }
  }
}