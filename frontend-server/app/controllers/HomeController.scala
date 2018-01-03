package controllers

import actors.ClientSocketActorFactory
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

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents)(jshellLauncher: JShellLauncher, clientSocketActorFactory: ClientSocketActorFactory, shellSocketActorFactory: ShellSocketActorFactory, config: Configuration)(implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) {
  def clientws = WebSocket.accept[InEvent, OutEvent] { request =>
    ActorFlow.actorRef { out =>
      clientSocketActorFactory.props(out, "123")
    }
  }
  
  def shellws = WebSocket.accept[OutEvent, InEvent] { request =>
    ActorFlow.actorRef { out =>
      shellSocketActorFactory.props(out, "123")
    }
  }
  
  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] =>{
    Ok(views.html.index())
  }
  }
}
