package actors

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.cluster.pubsub.{DistributedPubSubMediator, DistributedPubSub}
import Messages._

object ShellSocketActor {
  def props(out: ActorRef, gid: String) = Props(new ShellSocketActor(out, gid))
}

class ShellSocketActor(out: ActorRef, gid: String) extends Actor with ActorLogging {
  val toShellId = "s" + gid
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
  }
}