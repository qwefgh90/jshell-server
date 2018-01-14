package clients

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._
import com.typesafe.scalalogging.Logger
import java.nio.file.WatchService
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.WatchKey
import scala.collection.JavaConverters._
import java.nio.file.WatchEvent
import java.nio.file.Path
import clients.Mode._

object EntryPoint {
  def main(args: Array[String]): Unit = {
    import Mode._
    val logger = Logger(EntryPoint.getClass)
    implicit val system = ActorSystem("remote-jshell") 
    implicit val mat = ActorMaterializer()
    implicit val conf = ConfigFactory.load()
    val url = conf.getString("url")
    val sid = conf.getString("sid")
    val mode = conf.getString("client.mode")
    
    try{
      if(mode == Mode.Multiple){
        val watchPath = conf.getString("client.mode.multiple-watch-path")
        if(watchPath == "")
          throw new RuntimeException("client.mode.multiple-watch-path is empty")
        else{
          //directory changes
          val watcher = FileSystems.getDefault().newWatchService()
          val path = Paths.get(watchPath)
          var stopFlag = false
          path.register(watcher, ENTRY_CREATE)
          while(!stopFlag) {
            val key = watcher.take()
            val events = key.pollEvents().asScala
            events.foreach(event => {
              val kind = event.kind()
              if(kind != OVERFLOW){
                val ev = event.asInstanceOf[WatchEvent[Path]]
                val fileName = ev.context()
                //read url, sid
                //connect to jshell-server
              }
            })
            
            val vaild = key.reset()
            if(!vaild)
              stopFlag = true
          }
        }
        
      }else {
       	val client = WebSocketClient(url, sid)
       	Await.result(client.connect().future, Duration.Inf)
       	logger.info("remote jshell is terminated normally")
      } 
    }catch{
      case e: Exception => {
        logger.error("A error occurs during stopping.", e)
      }
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