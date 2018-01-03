package actors

import javax.inject._
import play.api.Configuration
import play.core.server.ServerConfig
import clients.WebSocketClient
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import scala.concurrent.{ExecutionContext, Future}

class LocalJShellLauncher @Inject()(config: Configuration)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends JShellLauncher{
  override def launch(key: String): Unit = {
    val port = config.getInt("http.port").getOrElse(9000)
    val url = s"ws://localhost:${port}/shellws"
    println("url: "+ url)
    val client = WebSocketClient(url, key)
    Future{
      client.connect()
    }
  }
}