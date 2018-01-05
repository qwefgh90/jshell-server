package clients

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._
import com.typesafe.scalalogging.Logger

object EntryPoint {
  def main(args: Array[String]): Unit = {
    val logger = Logger(EntryPoint.getClass)
    implicit val system = ActorSystem("remote-jshell") 
    implicit val mat = ActorMaterializer()
    val conf = ConfigFactory.load();
    val url = conf.getString("url")
    val sid = conf.getString("sid")
    val client = WebSocketClient(url, sid)
    Await.result(client.connect().future, Duration.Inf)
  }
}