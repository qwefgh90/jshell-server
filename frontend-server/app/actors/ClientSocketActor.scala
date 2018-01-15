package actors

import javax.inject.{Inject, Singleton}
import akka.actor._
import play.api.{Configuration, Application}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.{DistributedPubSubMediator, DistributedPubSub}
import Messages._
import FSMMeta._
import scala.concurrent.duration._
import akka.util.Timeout
import java.util.concurrent.TimeUnit

class ClientSocketActorFactory @Inject()(config: Configuration, jshellLauncher: JShellLauncher) {
  def props(out: ActorRef, sid: String) = Props(new ClientSocketActor(out, sid, config, jshellLauncher))
}

class ClientSocketActor(out: ActorRef, sid: String, config: Configuration, jshellLauncher: JShellLauncher) extends FSM[State,Data] {
  import DistributedPubSubMediator.{ Subscribe, SubscribeAck, Publish }
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(sid, self)
  val jshellTimeout = config.get[Int]("akka.actor.fsm.init-state-timeout")
  
  
  this.startWith(Uninitialized, UninitializedData(Vector[InEvent]()), Some(FiniteDuration(jshellTimeout, TimeUnit.MILLISECONDS)))
  
  when(Uninitialized) {
    case Event(e @ InEvent(t,m), d @ UninitializedData(list)) => {
      log.debug("received: " + e.toString)
      stay using d.copy(queue = list :+ e)
    }
    case Event(e @ SetTarget(ref), d @ UninitializedData(list)) if (ref != self) => {
      log.debug("received: " + e.toString)
      ref ! SetTarget(self) // send reference
      list.foreach(inEvent => {ref ! inEvent}) // flush all data to shell actor
      goto(Interactive) using InteractiveData(ref)
    }
  }
  
  when(Interactive) {
    case Event(e @ InEvent(t,m), d @ InteractiveData(ref)) if e.t == MessageType.ic.toString => {
      log.debug("dropped: " + e.toString)
      stay
    }
    case Event(e @ InEvent(t,m), d @ InteractiveData(ref)) => {
      log.debug("send: " + e.toString)
      ref ! e // send data to shell
      stay
    }
  }
  
  when(Interactive) {
    case Event(e @ OutEvent(t,m), d @ InteractiveData(ref)) if e.t == MessageType.ic.toString && e.m == InternalControlValue.terminate.toString => {
      log.debug("request to terminate")
      stop()
    }
    case Event(e @ OutEvent(t,m), d @ InteractiveData(ref)) => {
      log.debug("received: " + e.toString)
      out ! e
      stay
    }
  }
  
  whenUnhandled {
    case Event(e, d) => {
      log.debug("received unhandled request {} in state {}/{}", e, stateName, d)
      stay
    }
  }
  
  onTermination {
    case StopEvent(FSM.Normal, state, data) => {
      if(state == Uninitialized){
      }else{
        val d = data.asInstanceOf[InteractiveData]
        log.info("normal terminated")
        d.actorRef ! InEvent(MessageType.ic.toString, InternalControlValue.terminate.toString)
      }
    }
    case StopEvent(FSM.Shutdown, state, data) => {
      if(state == Uninitialized){
      }else{
        val d = data.asInstanceOf[InteractiveData]
        log.info("shutdown terminated")
        d.actorRef ! InEvent(MessageType.ic.toString, InternalControlValue.terminate.toString)
      }
    }
    case StopEvent(FSM.Failure(cause), state, data) =>{
      if(state == Uninitialized){
      }else{
        val d = data.asInstanceOf[InteractiveData]
        log.info("failure terminated")
        d.actorRef ! InEvent(MessageType.ic.toString, InternalControlValue.terminate.toString)
      }
    }
  }
  
  this.initialize()
  
  jshellLauncher.launch(sid)
}
