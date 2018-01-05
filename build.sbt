import Dependencies._

name := "jshell-server"
version := "0.0.1"

lazy val common = (project in file("common")).
  settings(
    name := "common",
    organization := "io.github.qwefgh90",
    scalaVersion := "2.12.3",
    version      := "0.1.0-SNAPSHOT",
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.7",
    libraryDependencies += "com.typesafe.play" %% "play" % "2.6.10",
	libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.8",    
    libraryDependencies += scalaTest % Test
)

lazy val frontend_server: Project = (project in file("frontend-server"))
.dependsOn(common).dependsOn(remote_jshell).enablePlugins(PlayScala, LauncherJarPlugin).settings(
  name := """frontend-server""",
  organization := "io.github.qwefgh90",
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.12.3",
  libraryDependencies += guice,
  libraryDependencies += "com.typesafe.akka" %% "akka-cluster" % "2.5.8",
  libraryDependencies += "com.typesafe.akka" %% "akka-remote" % "2.5.8",
  libraryDependencies += "com.typesafe.akka" %% "akka-cluster-tools" % "2.5.8",
  libraryDependencies += "javax.xml.bind" % "jaxb-api" % "2.3.0",
  libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.11",
  libraryDependencies += "com.typesafe.akka" %% "akka-http-testkit" % "10.0.11" % Test,
  libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
  libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.5.8" % Test,
  libraryDependencies += specs2 % Test
)

lazy val remote_jshell = (project in file("remote-jshell")).dependsOn(common).enablePlugins(JavaAppPackaging).settings(
  name := "remote-jshell",
  organization := "io.github.qwefgh90",
  scalaVersion := "2.12.3",
  version      := "0.1.0-SNAPSHOT",
  libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.4",
  libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.11",
  libraryDependencies += "com.typesafe.akka" %% "akka-http-testkit" % "10.0.11" % Test,
  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
  libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
  libraryDependencies += scalaTest % Test
)
