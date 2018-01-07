package controllers

import scala.util.Either
import play.api.mvc.RequestHeader

trait SidHandler {
  def handle(implicit rh: RequestHeader): Either[String, String]
}