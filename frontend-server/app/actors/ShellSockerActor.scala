package actors

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.{DistributedPubSubMediator, DistributedPubSub}
import Messages._

object ShellSocketActor {
  def props(out: ActorRef, sid: String) = Props(new ShellSocketActor(out, sid))
}

class ShellSocketActor(out: ActorRef, sid: String) extends FSM[State,Data] {
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
    case Event(e @ OutEvent(t,m), d @ InteractiveData(ref)) => {
      log.debug("send: " + e.toString)
      ref ! e // send data to client
      stay
    }
    case Event(e @ InEvent(t,m), d @ InteractiveData(ref)) => {
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
  this.initialize()
  
  mediator ! Publish(sid, SetTarget(self)) // send reference
  
  /*val toShellId = "s" + gid
  val toClientId = "c" + gid
  import DistributedPubSubMediator.{ Subscribe, SubscribeAck, Publish }
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(toShellId, self)
  def receive = {
    case subMsg: SubMessage => {
      out ! subMsg.text // forward
    }
    case msg: String => {
      log.info("got: " + msg)
      mediator ! Publish(toClientId, msg)
    }
  }*/
}