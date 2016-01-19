package com.azavea.usaceflood.server

import spray.httpx.SprayJsonSupport
import spray.json._

case class elevationArgs (multiPolygon: JsObject)
case class floodPercentagesArgs (multiPolygon: JsObject, minElevation: Double, floodLevels: Array[Double])
case class floodTilesArgs (multiPolygon: JsObject, minElevation: Double, maxElevation: Double, floodLevel: Double)

object JsonProtocol extends SprayJsonSupport {
  import DefaultJsonProtocol._

  implicit val elevationFormat = jsonFormat1(elevationArgs)
  implicit val floodPercentagesFormat = jsonFormat3(floodPercentagesArgs)
  implicit val floodTilesFormat = jsonFormat4(floodTilesArgs)
}
