import play.sbt.PlayRunHook
import sbt._
import java.net.InetSocketAddress
import scala.sys.process.Process

object Angular2 {
  def apply(log: Logger, base: File, npm: String, target: File): PlayRunHook = {

    object Angular2Process extends PlayRunHook {

      private var watchProcess: Option[Process] = None

      private def runProcessSync(command: String) = {
        log.info(s"Running $command...")
        val rc = Process(command, base).!
        if (rc != 0) {
          throw new Angular2Exception(s"$command failed with $rc")
        }
      }

      override def beforeStarted(): Unit = {
        val cacheFile = target / "package-json-last-modified"
        val cacheLastModified = if (cacheFile.exists()) {
          try {
            IO.read(cacheFile).trim.toLong
          } catch {
            case _: NumberFormatException => 0l
          }
        }
        val lastModified = (base / "package.json").lastModified()
        // Check if package.json has changed since we last ran this
        if (cacheLastModified != lastModified) {
          runProcessSync(s"${npm} install")
          IO.write(cacheFile, lastModified.toString)
        }
      }

      override def afterStarted(addr: InetSocketAddress): Unit = {
        watchProcess = Some(Process(s"${npm} run-script ng build -- --watch --delete-output-path=false", base).run)
      }

      override def afterStopped(): Unit = {
        watchProcess.foreach(_.destroy())
        watchProcess = None
      }
    }

    Angular2Process
  }
}

class Angular2Exception(message: String) extends RuntimeException(message) with FeedbackProvidedException