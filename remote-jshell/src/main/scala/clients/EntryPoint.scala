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
    implicit val conf = ConfigFactory.load()
    val url = conf.getString("url")
    val sid = conf.getString("sid")
    try{
    	val client = WebSocketClient(url, sid)
    			Await.result(client.connect().future, Duration.Inf)
    			logger.info("remote jshell is terminated normally")
    }finally{
      system.terminate().onComplete((t) =>{
        t.map(terminated => {
            logger.info("actor system is terminated normally: " + terminated.toString())
        }).failed.map((t)=>{
          logger.error("stopping actor system failed", t)
        })
      })
    }
  }
}