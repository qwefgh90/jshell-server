package actors

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.{DistributedPubSubMediator, DistributedPubSub}
import Messages._
import FSMMeta._
import scala.concurrent.duration._

object ClientSocketActor {
  def props(out: ActorRef, sid: String) = Props(new ClientSocketActor(out, sid))
}

class ClientSocketActor(out: ActorRef, sid: String) extends FSM[State,Data] {
  import DistributedPubSubMediator.{ Subscribe, SubscribeAck, Publish }
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(sid, self)
  
  this.startWith(Uninitialized, UninitializedData(Vector[InEvent]()))
  
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
    case Event(e @ InEvent(t,m), d @ InteractiveData(ref)) => {
      log.debug("send: " + e.toString)
      ref ! e // send data to shell
      stay
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
  
  this.initialize()
}
