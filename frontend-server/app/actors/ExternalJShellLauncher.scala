package actors

import javax.inject._
import play.api.Configuration
import play.core.server.ServerConfig
import clients.WebSocketClient
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import play.api.Logger

class ExternalJShellLauncher @Inject()(config: Configuration)(implicit system: ActorSystem, mat: Materializer) extends JShellLauncher{
  override def launch(key: String): Unit = {
    val runPath = config.get[String]("custom.shell.external.runPath")
    if(runPath != ""){
      val proc = Runtime.getRuntime.exec(s"cmd /c ${runPath} -Durl=${url} -Dsid=${key}")
      Logger.info(s"Remote JShell information (${proc.pid()}) \n ${proc.info().toString()}")
    }
  }
}
