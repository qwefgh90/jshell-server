
// Adds additional packages into Twirl
//TwirlKeys.templateImports += "io.github.qwefgh90.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "io.github.qwefgh90.binders._"

lazy val angular2BaseDir = settingKey[File]("Base directory for Angular 2 app")
angular2BaseDir := file("""C:\workspace\jshell-client""") // eg, baseDirectory.value / "my-angular-app"

lazy val npmPathString = settingKey[String]("npm path")
npmPathString := """"C:\Program Files\nodejs\npm.cmd"""" // eg, baseDirectory.value / "my-angular-app"

PlayKeys.playRunHooks += Angular2(streams.value.log, angular2BaseDir.value, npmPathString.value, target.value)
// Sets the Angular output directory as Play's public directory. This completely replaces the
// public directory, if you want to use this in addition to the assets in the public directory,
// then use this instead:
// unmanagedResourceDirectories in Assets += angular2BaseDir.value / "dist"
resourceDirectory in Assets := angular2BaseDir.value / "dist"

lazy val build = taskKey[Unit]("Base directory for Angular 2 app")
build := { 
  scala.sys.process.Process(s"${npmPathString.value} run-script ng build -- --prod --delete-output-path=false", angular2BaseDir.value).!
}