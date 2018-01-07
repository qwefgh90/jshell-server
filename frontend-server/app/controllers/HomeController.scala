package controllers

import actors.ClientSocketActorFactory
import play.Logger;
import actors.JShellLauncher
import actors.Messages.InEvent
import actors.Messages.OutEvent
import actors.Messages.messageFlowTransformer
import actors.Messages.messageReverseFlowTransformer
import actors.ShellSocketActorFactory
import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.Inject
import javax.inject.Singleton
import play.api.Configuration
import play.api.libs.streams.ActorFlow
import play.api.mvc.AbstractController
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.mvc.Request
import play.api.mvc.WebSocket
import play.Application
import scala.concurrent.Future
import java.io.File
import scala.concurrent.ExecutionContext

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents)(jshellLauncher: JShellLauncher, clientSocketActorFactory: ClientSocketActorFactory, shellSocketActorFactory: ShellSocketActorFactory, config: Configuration, sidHandler: SidHandler)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends AbstractController(cc) {
  def clientws = WebSocket.acceptOrResult[InEvent, OutEvent] { implicit request =>
    val sidOpt = sidHandler.handle(request).toOption
    Logger.debug(s"Current client ip: ${request.remoteAddress} sid: ${sidOpt}")
    Future.successful{
      if(sidOpt.isDefined){
       Right{
   	   ActorFlow.actorRef { out =>
   	     clientSocketActorFactory.props(out, sidOpt.get)
   	   }}
      }else{
        Left(Forbidden)
      }
    }
  }
  
  def shellws = WebSocket.acceptOrResult[OutEvent, InEvent] { request =>
  	 Logger.debug(request.headers.get("Authorization").get.toString())
  	 Future.successful(
  	  request.headers.get("Authorization") match {
  	  case Some(base64Decoded) => Right{
  	      val sid = akka.http.scaladsl.model.headers.BasicHttpCredentials(base64Decoded.substring(6)).password
          Logger.debug(s"Current shell sid: ${sid}")
  		  	ActorFlow.actorRef { out =>
  		  	shellSocketActorFactory.props(out, sid)
  		  	}
  	    }
  	  case None => {
          Logger.debug(s"Forbidden. sid header is empty")
  	      Left(Forbidden)
  	    }
  	  }
		)
  }
  
  def root = Action.async{ req =>
    val sid = req.session.get("sid").getOrElse(java.util.UUID.randomUUID().toString())
    Assets.at("index.html").apply(req).map(res => res.withSession("sid" -> sid))
  }
}
