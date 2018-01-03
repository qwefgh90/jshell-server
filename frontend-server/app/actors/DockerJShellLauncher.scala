package actors

import javax.inject._
import play.api.Configuration
import play.core.server.ServerConfig
import clients.WebSocketClient
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }

class DockerJShellLauncher @Inject()(config: Configuration)(implicit system: ActorSystem, mat: Materializer) extends JShellLauncher{
  override def launch(key: String): Unit = {
    
  }
}
