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
  
  object MessageType extends Enumeration {
	  type MessageType = Value
		val i = Value // in  (from client to shell)
		val o = Value // out (from shell to client)
		val ic = Value // internal control
		val uc = Value // user control
  }
  
  object UserControlValue extends Enumeration {
	  type UserControlValue = Value
  }
  
  object InternalControlValue extends Enumeration {
	  type InternalControlValue = Value
		val terminate = Value // in  (from client to shell)
		val start = Value // out (from shell to client)
  }
}