package actors

import javax.inject._
import play.api.Configuration
import play.core.server.ServerConfig
import clients.WebSocketClient
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import scala.concurrent.{ExecutionContext, Future}

class LocalJShellLauncher @Inject()(config: Configuration)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends JShellLauncher{
  implicit val typesafeConfig = config.underlying
  lazy override val port = config.getInt("http.port").orElse{
    if(System.getProperty("http.port") != null) 
      Some(Integer.parseInt(System.getProperty("http.port"))) 
    else None
  }.getOrElse(9000)
  override def launch(key: String): Unit = {
    val client = WebSocketClient(url, key)
    Future{
      client.connect()  
    }
  }
}