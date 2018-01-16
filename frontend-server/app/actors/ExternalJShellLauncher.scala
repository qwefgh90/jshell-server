package actors

import javax.inject._
import play.api.Configuration
import play.core.server.ServerConfig
import clients.WebSocketClient
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import play.api.Logger
import org.apache.commons.lang3.SystemUtils
import scala.concurrent.Future
import java.io.BufferedReader
import java.io.InputStreamReader
import scala.concurrent.ExecutionContext.Implicits.global

class ExternalJShellLauncher @Inject()(config: Configuration)(implicit system: ActorSystem, mat: Materializer) extends JShellLauncher{
  lazy val hostname = config.get[String]("shell.external.connecting-hostname")
  override lazy val port = config.getInt("http.port").getOrElse(9000)
  override def launch(key: String): Unit = {
    val url = s"ws://${hostname}:${port}/shellws"
    val runPath = config.get[String]("shell.external.path")
    if(runPath != ""){
      val command = if (SystemUtils.IS_OS_WINDOWS) s"cmd /c ${runPath} -Durl=${url} -Dsid=${key}"
        else s"""${runPath} -Durl=${url} -Dsid=${key}"""
        
      val proc = Runtime.getRuntime.exec(command)
      Logger.info(s"Remote JShell information (${proc.pid()}) \n ${proc.info().toString()} \n command: ${command}")
    }
  }
}
