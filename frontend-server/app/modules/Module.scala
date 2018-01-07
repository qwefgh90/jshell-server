package modules

import com.google.inject.AbstractModule

import actors._
import play.api.Configuration
import play.api.Environment
import controllers.SidHandler
import controllers.DefaultSidHandler
import play.api.Mode

class Module(
  environment: Environment,
  config: Configuration) extends AbstractModule {
  def configure() = {
    implicit val mode = environment.mode
    implicit val shellMode = config.getString("custom.shell.mode").getOrElse("none")
    bindingJShellLauncher()
    bindingSidHandler()
  }
  
  def bindingSidHandler()(implicit mode: Mode, shellMode: String){
    bind(classOf[SidHandler])
      .to(classOf[DefaultSidHandler])
  }
  
  def bindingJShellLauncher()(implicit mode: Mode, shellMode: String){
    if(mode == play.api.Mode.Prod){
      if(shellMode == "docker")
        bind(classOf[JShellLauncher])
          .to(classOf[DockerJShellLauncher])
      else
        bind(classOf[JShellLauncher])
          .to(classOf[LocalJShellLauncher])
    }else if(mode == play.api.Mode.Dev){
      bind(classOf[JShellLauncher])
        .to(classOf[LocalJShellLauncher])
    }else if(mode == play.api.Mode.Test){
      bind(classOf[JShellLauncher])
        .to(classOf[LocalJShellLauncher])
    }
  }
}