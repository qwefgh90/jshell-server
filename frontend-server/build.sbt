import org.apache.commons.lang3.SystemUtils

lazy val angular2BaseDir = settingKey[File]("Base directory for Angular 2 app")
lazy val npmPathString = taskKey[String]("npm path")

angular2BaseDir := file(baseDirectory.value + """/../../jshell-client""") // eg, baseDirectory.value / "my-angular-app"
npmPathString := {
	if(SystemUtils.IS_OS_WINDOWS)
  	  """cmd /c npm"""
  	else
  	  ""
}

PlayKeys.playRunHooks += Angular2(streams.value.log, angular2BaseDir.value, npmPathString.value, target.value)
// Sets the Angular output directory as Play's public directory. This completely replaces the
// public directory, if you want to use this in addition to the assets in the public directory,
// then use this instead:
unmanagedResourceDirectories in Assets += angular2BaseDir.value / "dist"
// resourceDirectory in Assets := angular2BaseDir.value / "dist"

lazy val set = taskKey[Unit]("set")
set := {
	 scala.sys.process.Process("cmd /c npm install").!
}

lazy val ngInstall = taskKey[Unit]("npm install task")
ngInstall := { 
  println("> npm install")
  println("\nnode_modules is being set up")
  scala.sys.process.Process(s"${npmPathString.value} install", angular2BaseDir.value).!
}

lazy val ngDevBuild = taskKey[Unit]("ng build dev task")
ngDevBuild := { 
  scala.sys.process.Process(s"${npmPathString.value} run-script ng build -- --delete-output-path=false", angular2BaseDir.value).!
}

lazy val ngBuild = taskKey[Unit]("ng build task")
ngBuild := { 
  scala.sys.process.Process(s"${npmPathString.value} run-script ng build -- --prod --delete-output-path=false", angular2BaseDir.value).!
}

// angular cli integration 
stage := (stage dependsOn ngBuild dependsOn ngInstall).value
dist := (dist dependsOn ngBuild dependsOn ngInstall).value