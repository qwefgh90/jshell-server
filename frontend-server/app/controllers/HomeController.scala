package controllers

import javax.inject._
import play.api.Configuration
import play.api._
import play.api.mvc._
import play.api.libs.streams.ActorFlow
import actors.Messages._
import actors.{ ShellSocketActorFactory, ClientSocketActorFactory }
import akka.actor.ActorSystem
import akka.stream.Materializer

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(cc: ControllerComponents)(clientSocketActorFactory: ClientSocketActorFactory, shellSocketActorFactory: ShellSocketActorFactory, config: Configuration)(implicit system: ActorSystem, mat: Materializer) extends AbstractController(cc) {
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
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }
}
