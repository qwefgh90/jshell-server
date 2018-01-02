package actors

import akka.actor.ActorRef
import play.api.mvc.WebSocket.MessageFlowTransformer

object Messages {
  import play.api.libs.json._

  implicit val inEventFormat = Json.format[InEvent]
  implicit val outEventFormat = Json.format[OutEvent]

  implicit val messageFlowTransformer = MessageFlowTransformer.jsonMessageFlowTransformer[InEvent, OutEvent]
  implicit val messageReverseFlowTransformer = MessageFlowTransformer.jsonMessageFlowTransformer[OutEvent, InEvent]
  
  case class SetTarget(actorRef: ActorRef) 
  case class InEvent(t: String, m: String)
  case class OutEvent(t: String, m: String)
}