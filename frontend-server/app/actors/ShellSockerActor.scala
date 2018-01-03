package actors

import javax.inject.{Inject, Singleton}
import akka.actor._
import akka.cluster.Cluster
import play.api.{Configuration, Application}
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.{DistributedPubSubMediator, DistributedPubSub}
import Messages._
import FSMMeta._

class ShellSocketActorFactory @Inject()(config: Configuration) {
  def props(out: ActorRef, sid: String) = Props(new ShellSocketActor(out, sid, config))
}

class ShellSocketActor(out: ActorRef, sid: String, config: Configuration) extends FSM[State,Data] {
  import DistributedPubSubMediator.{ Subscribe, SubscribeAck, Publish }
  val mediator = DistributedPubSub(context.system).mediator
  
  this.startWith(Uninitialized, UninitializedData(Vector[OutEvent]()))
  
  when(Uninitialized) {
    case Event(e @ OutEvent(t,m), d @ UninitializedData(list)) => {
      log.debug("received: " + e.toString)
      stay using d.copy(list :+ e) 
    }
    case Event(e @ SetTarget(ref), UninitializedData(list)) if (ref != self) => {
      log.debug("received: " + e.toString)
      list.foreach(e => ref ! e) // flush all data to client actor
      goto(Interactive) using InteractiveData(ref)
    }
  }
  
  when(Interactive) {
    case Event(e @ InEvent(t,m), d @ InteractiveData(ref)) => {
      log.debug("received: " + e.toString)
      out ! e 
      stay
    }
  }
  
  when(Interactive) {
    case Event(e @ OutEvent(t,m), d @ InteractiveData(ref)) => {
      log.debug("send: " + e.toString)
      ref ! e // send data to client
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
        d.actorRef ! OutEvent(MessageType.ic.toString, InternalControlValue.terminate.toString)
      }
    }
    case StopEvent(FSM.Shutdown, state, data)       =>{
      if(state == Uninitialized){
      }else{
        val d = data.asInstanceOf[InteractiveData]
        log.info("shutdown terminated")
        d.actorRef ! OutEvent(MessageType.ic.toString, InternalControlValue.terminate.toString)
      }
    }
    case StopEvent(FSM.Failure(cause), state, data) =>{
      if(state == Uninitialized){
      }else{
        val d = data.asInstanceOf[InteractiveData]
        log.info("failure terminated")
        d.actorRef ! OutEvent(MessageType.ic.toString, InternalControlValue.terminate.toString)
      }
    }
  }
  
  this.initialize()
  
  mediator ! Publish(sid, SetTarget(self)) // send reference
}