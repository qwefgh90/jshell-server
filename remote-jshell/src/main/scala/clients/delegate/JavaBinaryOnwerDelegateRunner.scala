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
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import java.util.concurrent.CompletableFuture

object JavaBinaryOnwerDelegateRunner{
	final case class Delegate(id: String, javaHome: Path)
	final case class Cleaning()
	final case class DeleteUser(id: String)
	
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
      //1) useradd
      var future = runtime.exec(s"useradd $newId").onExit()
            .thenApply[List[Process]]((proc) => (proc :: Nil))
      //useradd.waitFor(2000, TimeUnit.SECONDS)
      //list :+ useradd.info()
      val script = s"""su -s /bin/sh $newId""" + " -c \"" + tempJavaPath.toString() +" $1 $2 $3 $4 $5 $6 $7 $8 $9 $10 $11 $12 $13\" "
      //2) backup binary
      if(!Files.exists(tempJavaPath)){
        future = future.thenCompose((list) => runtime.exec(s"cp -n ${javaPath.toString()} ${tempJavaPath.toString()}").onExit()
              .thenApply[List[Process]]((proc) => (proc :: list)))
        future = future.thenCompose((list) => runtime.exec(s"rm -f ${javaPath.toString()}").onExit()
              .thenApply[List[Process]]((proc) => (proc :: list)))
      }
      //3) create new script
      future = future.thenCompose((list) => {
        CompletableFuture.supplyAsync(() => {
          Files.write(javaPath, script.getBytes)
          list
        })
      })
      future = future.thenCompose((list) => runtime.exec(s"chmod +x ${javaPath.toString()}").onExit()
            .thenApply[List[Process]]((proc) => (proc :: list)))
      try{
        val success = future.get(60000, TimeUnit.MILLISECONDS)
        log.info("setup info \n" + success.map(_.toString()).mkString("\n"))
        newId
      }catch{
        case e => {
          log.error(e, "A error occurs during setup.")
          ""
        }
      }
    }else
      ""
  }
  
  def doCleanJavaBinary(id: String, javaHome: Path): String = {
    if(SystemUtils.IS_OS_LINUX){
      val javaPath = javaHome.resolve("bin").resolve("java")
      val tempJavaPath = javaHome.resolve("bin").resolve("safejava")
      //1) remove new script
      //2) recover binary
      var future = runtime.exec(s"rm -f ${javaPath.toString()}").onExit().orTimeout(1000, TimeUnit.MILLISECONDS)
            .thenApply[List[Process]]((proc) => (proc :: Nil))
      future = future.thenCompose((list) => {
        runtime.exec(s"cp ${tempJavaPath.toString} ${javaPath.toString}")
        .onExit().orTimeout(1000, TimeUnit.MILLISECONDS)
        .thenApply[List[Process]]((proc) => (proc :: list))
      })
      try{
        val success = future.get(3000, TimeUnit.MILLISECONDS)
        log.info("clean info \n" + success.map(_.toString()).mkString("\n"))
        id
      }catch{
        case e => {
          log.error(e, "A error occurs during clean.")
          ""
        }
      }
    }else
      ""
  }
  
  def doDeleteUser(id: String){
    if(SystemUtils.IS_OS_LINUX){
      val br = new BufferedReader(new InputStreamReader(runtime.exec(s"userdel $id").getErrorStream))
      val error = br.readLine()
      if(error != null){
        log.debug(s"kill ${error.split(" ").last} and userdel ${id}")
        var future = runtime.exec(s"kill ${error.split(" ").last}").onExit().orTimeout(3000, TimeUnit.MILLISECONDS)
                    .thenApply[List[Process]]((proc) => (proc :: Nil))
        future = future.thenCompose((list) => runtime.exec(s"userdel -f $id").onExit().orTimeout(3000, TimeUnit.MILLISECONDS)
                    .thenApply[List[Process]]((proc) => (proc :: list)))
        try{
          val success = future.get(6000, TimeUnit.MILLISECONDS)
          log.info("delete info \n" + success.map(_.toString()).mkString("\n"))
        }catch{
          case e => {
            log.error(e, "A error occurs during delete.")
          }
        }
      }else{
        log.info("delete info \n" + id)
      }
    }
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
    case Event(DeleteUser(id), _) => {
      doDeleteUser(id)
      stay
    }
    case Event(e, d) => {
      log.debug("received unhandled request {} in state {}/{}", e, stateName, d)
      stay
    }
  }
  
  initialize()
}