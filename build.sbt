import Dependencies._

name := "jshell-server"
version := "0.0.1"

lazy val frontend_server = (project in file("frontend-server")).enablePlugins(PlayScala).settings(
  name := """frontend-server""",
  organization := "io.github.qwefgh90",
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.12.3",
  libraryDependencies += guice,
  libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
)
lazy val remote_jshell = (project in file("remote-jshell")).settings(
  name := "remote-jshell",
  organization := "com.example",
  scalaVersion := "2.12.4",
  version      := "0.1.0-SNAPSHOT",
  libraryDependencies += scalaTest % Test
)
