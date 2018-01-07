package controllers

import play.api.mvc.RequestHeader
import javax.inject.Inject

class TestSidHandler(sid: String) extends SidHandler {
  override def handle(implicit rh: RequestHeader = null): Either[String, String] = {
    Right(sid)
  }
}