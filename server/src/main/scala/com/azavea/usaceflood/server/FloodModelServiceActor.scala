package com.azavea.usaceflood.server

import geotrellis.proj4._
import geotrellis.vector._
import geotrellis.vector.io.json._
import geotrellis.vector.reproject._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.spark._

import akka.actor._

import spray.routing._
import spray.http.MediaTypes
import spray.http.HttpHeaders._
import spray.http.HttpMethods._
import spray.http.{ AllOrigins, MediaTypes }
import spray.http.{ HttpMethods, HttpMethod, HttpResponse, AllOrigins }
import spray.httpx.SprayJsonSupport._
import spray.json._

import org.apache.spark._

import scala.concurrent._

class FloodModelServiceActor(sc: SparkContext) extends Actor with HttpService {
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val _sc = sc

  val nativeCRS = CRS.fromEpsgCode(4269)

  def actorRefFactory = context
  def receive = runRoute(root)

  val corsHeaders = List(`Access-Control-Allow-Origin`(AllOrigins),
    `Access-Control-Allow-Methods`(GET, POST, OPTIONS, DELETE),
    `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent, Access-Control-Request-Method, Access-Control-Request-Headers"))

  def cors: Directive0 = {
    val rh = implicitly[RejectionHandler]
    respondWithHeaders(corsHeaders) & handleRejections(rh)
  }

  def root =
    path("ping") { complete { "OK" } } ~
    pathPrefix("min-elevation") { minElevationRoute } ~
    pathPrefix("flood-percentages") { floodPercentagesRoute } ~
    pathPrefix("flood-tiles") { floodTilesRoute }

  def minElevationRoute =
    cors {
      import spray.json.DefaultJsonProtocol._

      parameters('polygon) { (geojson) =>
        complete {
          future {
            val polygon = geojson.parseGeoJson[Polygon].reproject(LatLng, nativeCRS)
            JsObject(
              "minElevation" -> JsNumber(MinElevation(polygon))
            )
          }
        }
      }
    }

  def floodPercentagesRoute =
    cors {
      import spray.json.DefaultJsonProtocol._

      parameters('polygon, 'minElevation.as[Double], 'floodLevels) { (geojson, minElevation, floodLevelsString) =>
        complete {
          future {
            val polygon = geojson.parseGeoJson[Polygon].reproject(LatLng, nativeCRS)
            val floodLevels = floodLevelsString.split(',').map(_.toDouble)
            FloodPercentages(polygon, floodLevels, minElevation)
          }
        }
      }
    }

  def floodTilesRoute =
    pathPrefix(IntNumber / IntNumber / IntNumber) { (zoom, x, y) =>
      parameters('polygon, 'minElevation.as[Double], 'floodLevel.as[Double]) { (geojson, minElevation, floodLevel) =>
        respondWithMediaType(MediaTypes.`image/png`) {
          complete {
            future {

              val polygon = geojson.parseGeoJson[Polygon].reproject(LatLng, WebMercator)
              val key = SpatialKey(x, y)

              ElevationData(zoom, key, polygon) match {
                case Some(tile) =>
                  val floodTile = FloodTile(tile, polygon, minElevation, floodLevel)

                  // Paint the tile

                  floodTile.renderPng(ColorRamps.BlueToRed).bytes

                case None =>
                  Array[Byte]()
              }
            }
          }
        }
      }
    }
}
