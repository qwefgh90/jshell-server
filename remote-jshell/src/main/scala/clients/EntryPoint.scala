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
import java.nio.file.Files
import akka.stream.Materializer
import com.typesafe.config.Config
import java.io.BufferedReader

object EntryPoint {
  val logger = Logger(EntryPoint.getClass)
  
  /* 
   * 1) detect directory changes
   * 2) read url, sid
   * 3) connect to jshell-server
   */
  def runMultiple()(implicit system: ActorSystem, mat: Materializer, conf: Config) {
    val watchPath = conf.getString("client.multiple-watch-path")
    if(watchPath == "")
      throw new RuntimeException("client.multiple-watch-path is empty string")
    else{
      //detect directory changes
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
            val newPath = path.resolve(ev.context().getFileName)
            logger.info("new file : "+ newPath.toAbsolutePath().toString())
            if(newPath.getFileName.toString == "STOP"){
              stopFlag = true
            }else{
              var br: BufferedReader = null;
              try{
                Thread.sleep(500)
                br = Files.newBufferedReader(newPath)
                val keyValueList = Stream.continually(br.readLine).takeWhile(_ != null).map(s => s.splitAt(s.indexOf('=')))
                .foldLeft(Map[String, String]())((m: Map[String, String], tu) => {
                  m + (tu._1 -> tu._2.drop(1))
                })
                val urlOpt = keyValueList.get("url")
                val sidOpt = keyValueList.get("sid")
                val futureOpt = urlOpt.map(url => sidOpt.map(sid => {
       	          val client = WebSocketClient(urlOpt.get, sidOpt.get)
       	          client.connect().future 
                }))
                if(futureOpt.isEmpty)  
                  logger.warn(s"A sid or url is missing in ${newPath.toString()}")
              }catch{
                case e:Exception => {
                  logger.error("A error occurs while reading new file.", e)
                }
              }finally{
                if(br != null)
                  br.close()
              }
            }
            Files.delete(newPath)
          }
        })
        val vaild = key.reset()
        if(!vaild)
          stopFlag = true
      }
    }
  }
  def main(args: Array[String]): Unit = {
    import Mode._
    implicit val system = ActorSystem("remote-jshell") 
    implicit val mat = ActorMaterializer()
    implicit val conf = ConfigFactory.load()
    val mode = conf.getString("client.mode")
    try{
      logger.info(s"current running mode: ${mode.toString()}")
      if(mode == Mode.multiple.toString()){
        runMultiple()
      }else {
        val url = conf.getString("url")
        val sid = conf.getString("sid")
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