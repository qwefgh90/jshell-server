package controllers



import play.api.mvc.RequestHeader
import javax.inject.Inject

class DefaultSidHandler @Inject()() extends SidHandler {
  override def handle(implicit rh: RequestHeader): Either[String, String] = {
    rh.session.get("sid") match {
      case Some(sid) => Right(sid)
      case None => Left("sid doesn't exist in session.")
    }
  }
}