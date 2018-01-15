package clients.delegate

import java.nio.file.Path
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Actor
import org.apache.commons.lang3.SystemUtils
import akka.actor.{ ActorRef, FSM }
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.Future
import java.nio.file.Files
import scala.collection.mutable.MutableList
import java.lang.ProcessHandle.Info
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

object JavaBinaryOnwerDelegateRunner{
	final case class Delegate(id: String, javaHome: Path)
	final case class Cleaning()
	
	sealed trait State
	case object Ready extends State
	case object Progress extends State
	
	sealed trait Data
	final case object Uninitialized extends Data
	final case class Todo(id: String, javaHome: Path, sender: ActorRef, queue: Seq[Job]) extends Data
	final case class Job(id: String, javaHome: Path, actorRef: ActorRef)
	
  def props(): Props = Props(new JavaBinaryOnwerDelegateRunner())
}

class JavaBinaryOnwerDelegateRunner() 
  extends FSM[JavaBinaryOnwerDelegateRunner.State, JavaBinaryOnwerDelegateRunner.Data]{
  //override def preStart(): Unit = log.info("RemoteJShellRunner started")
  //override def postStop(): Unit = log.info("RemoteJShellRunner stopped")
  val runtime = Runtime.getRuntime

  import JavaBinaryOnwerDelegateRunner._
  startWith(Ready, Uninitialized)
  val temporaryFileNameForLinux = "safejava"
  
  def generateId(id: String): String = {
    val newId = (id 
        + UUID.randomUUID().toString().replaceAll("-", "") 
        + UUID.randomUUID().toString().replaceAll("-", "")).take(30)
    val br = new BufferedReader(new InputStreamReader(runtime.exec("id -u $newId").getInputStream))
    try{
    	if(br.readLine() == null)
    		newId
    	else
    		generateId(id)
    }finally{
    	br.close()
    }
  }
  
  def doSetupJavaBinary(id: String, javaHome: Path): String = {
    if(SystemUtils.IS_OS_LINUX){
      val newId: String = generateId(id)
      val javaPath = javaHome.resolve("bin").resolve("java")
      val tempJavaPath = javaHome.resolve("bin").resolve("safejava")
      val list = scala.collection.mutable.Seq[Info]()
      //1) useradd
      list :+ runtime.exec(s"useradd $newId")
      val script = s"""su -s /bin/bash $newId""" + " -c \"" + tempJavaPath.toString() +" $1 $2 $3 $4 $5 $6 $7 $8 $9 $10 $11 $12 $13\" "
      //2) backup binary
      if(!Files.exists(tempJavaPath)){
        list :+ runtime.exec(s"cp -n ${javaPath.toString()} ${tempJavaPath.toString()}")
        list :+ runtime.exec(s"rm -f ${javaPath.toString()}")
      }
      //3) create new script
      Thread.sleep(1000)
      Files.write(javaPath, script.getBytes)
      list :+ runtime.exec(s"chmod +x ${javaPath.toString()}")
      log.info("setup info \n" + list.mkString("\n"))
      newId
    }else
      ""
  }
  
  def doCleanJavaBinary(id: String, javaHome: Path): String = {
    if(SystemUtils.IS_OS_LINUX){
      val javaPath = javaHome.resolve("bin").resolve("java")
      val tempJavaPath = javaHome.resolve("bin").resolve("safejava")
      //1) remove new script
      //2) recover binary
      //3) userdel
      val list = List(runtime.exec(s"rm -f ${javaPath.toString()}").info(),
      runtime.exec(s"cp ${tempJavaPath.toString} ${javaPath.toString}").info(),
      runtime.exec(s"userdel $id").info())
      log.info("clean info \n" + list.mkString("\n"))
    }
    id
  }
  
  when(Ready){
    case Event(Delegate(id, javaHome), Uninitialized) => {
      val newId = doSetupJavaBinary(id, javaHome)
      this.sender() ! newId
      goto(Progress) using Todo(newId, javaHome, this.sender(), Seq[Job]())
    }
  }
  
  when(Progress){
    case Event(r @ Delegate(id, javaHome), todo: Todo) =>
      stay using todo.copy(queue = todo.queue :+ Job(r.id, r.javaHome, this.sender()))
    case Event(c @ Cleaning(), todo: Todo) => {
    	val newId = doCleanJavaBinary(todo.id, todo.javaHome)
    	this.sender() ! newId

      todo.queue match {
        case first :: rest => {
          val newId = doSetupJavaBinary(first.id, first.javaHome)
          first.actorRef ! newId
      	  stay using todo.copy(id = newId
      			  , javaHome = first.javaHome
      			  , queue = rest)
        }
        case Nil =>
          goto(Ready) using Uninitialized
      }
    }
  }
  
  whenUnhandled {
    case Event(e, d) => {
      log.debug("received unhandled request {} in state {}/{}", e, stateName, d)
      stay
    }
  }
  
  initialize()
}