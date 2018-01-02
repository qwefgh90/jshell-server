package actors

import akka.actor.ActorRef

object FSMMeta {
  sealed trait State
  case object Uninitialized extends State
  case object Interactive extends State
  
  sealed trait Data
  case class UninitializedData[T](queue: Seq[T]) extends Data
  case class InteractiveData(actorRef: ActorRef) extends Data
}