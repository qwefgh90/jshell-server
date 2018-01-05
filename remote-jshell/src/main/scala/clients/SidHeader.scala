package clients

import akka.http.scaladsl.model.headers.ModeledCustomHeader
import akka.http.scaladsl.model.headers.ModeledCustomHeaderCompanion
import scala.util.Try
import akka.http.scaladsl.model.headers.CustomHeader
/*
final class SidHeader(token: String) extends akka.http.scaladsl.model.headers.CustomHeader{
  override def name() = "sid"  
  override def value() = token
  override def renderInRequests = false
  override def renderInResponses = false
}
object SidHeader{
  def apply(token: String) = new SidHeader(token)
}*/

final class SidHeader(token: String) extends ModeledCustomHeader[SidHeader] {
  override def renderInRequests = false
  override def renderInResponses = false
  override val companion = SidHeader
  override def value: String = token
}

object SidHeader extends ModeledCustomHeaderCompanion[SidHeader] {
  override val name = "sid"
  override def parse(value: String) = Try(new SidHeader(value))
}