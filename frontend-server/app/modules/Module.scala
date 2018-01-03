package modules

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import actors._
import play.api.{ Configuration, Environment }

class Module(
  environment: Environment,
  config: Configuration) extends AbstractModule {
  def configure() = {
    val dockerMode = config.getBoolean("custom.dockerMode").getOrElse(false)
    if(dockerMode)
      bind(classOf[JShellLauncher])
        .to(classOf[DockerJShellLauncher])
    else
      bind(classOf[JShellLauncher])
        .to(classOf[LocalJShellLauncher])
  }
}