package actors

trait JShellLauncher {
  lazy val port = 9000
  lazy val url = s"ws://localhost:${port}/shellws"
  def launch(key: String): Unit
}