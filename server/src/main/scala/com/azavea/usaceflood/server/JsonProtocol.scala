package com.azavea.usaceflood.server

import spray.httpx.SprayJsonSupport
import spray.json._

case class minElevationArgs (multiPolygon: JsObject)
case class floodPercentagesArgs (multiPolygon: JsObject, minElevation: Double, floodLevels: Array[Double])
case class floodTilesArgs (multiPolygon: JsObject, minElevation: Double, floodLevel: Double)

object JsonProtocol extends SprayJsonSupport {
  import DefaultJsonProtocol._

  implicit val minElevationFormat = jsonFormat1(minElevationArgs)
  implicit val floodPercentagesFormat = jsonFormat3(floodPercentagesArgs)
  implicit val floodTilesFormat = jsonFormat3(floodTilesArgs)
}
