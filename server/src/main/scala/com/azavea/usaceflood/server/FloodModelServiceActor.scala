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
  import JsonProtocol._

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

      entity(as[minElevationArgs]) { (args) =>
        complete {
          future {
            val multiPolygon = args.multiPolygon.toString().parseGeoJson[MultiPolygon].reproject(LatLng, nativeCRS)
            JsObject(
              "minElevation" -> JsNumber(MinElevation(multiPolygon))
            )
          }
        }
      }
    }

  def floodPercentagesRoute =
    cors {
      import spray.json.DefaultJsonProtocol._

      entity(as[floodPercentagesArgs]) { (args) =>
        complete {
          future {
            val multiPolygon = args.multiPolygon.toString().parseGeoJson[MultiPolygon].reproject(LatLng, nativeCRS)
            FloodPercentages(multiPolygon, args.floodLevels, args.minElevation)
          }
        }
      }
    }

  def floodTilesRoute =
    rejectEmptyResponse {
      pathPrefix(IntNumber / IntNumber / IntNumber) { (zoom, x, y) =>
        entity(as[floodTilesArgs]) { (args) =>
          respondWithMediaType(MediaTypes.`image/png`) {
            complete {
              future {

                val multiPolygon = args.multiPolygon.toString().parseGeoJson[MultiPolygon].reproject(LatLng, WebMercator)
                val key = SpatialKey(x, y)

                ElevationData(zoom, key, multiPolygon) match {
                  case Some(tile) =>
                    val floodTile = FloodTile(tile, zoom, key, multiPolygon, args.minElevation, args.floodLevel)

                    // Paint the tile
                    val justBlueRamp = ColorRamp.createWithRGBColors(0x0000FF).setAlpha(127)
                    floodTile.renderPng(justBlueRamp).bytes

                  case None =>
                    Array[Byte]()
                }
              }
            }
          }
        }
      }
    }
}
