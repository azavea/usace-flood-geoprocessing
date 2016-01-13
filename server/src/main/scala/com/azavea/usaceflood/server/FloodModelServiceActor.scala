package com.azavea.usaceflood.server

import geotrellis.proj4._
import geotrellis.vector._
import geotrellis.vector.io.json._
import geotrellis.vector.reproject._
import geotrellis.raster.render._
import geotrellis.spark._

import akka.actor._

import spray.routing._
import spray.http.HttpHeaders._
import spray.http.HttpMethods._
import spray.http.{ AllOrigins, MediaTypes }
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
                    val breaks = ColorBreaks.fromStringDouble(
                      """0.0000:c2dae8b3;
                        |0.3048:a0bcd9b3;
                        |0.6096:7c9fc7b3;
                        |0.9144:4b81b3b3;
                        |1.2192:2c6ca0b3;
                        |1.5240:3d5485b3;
                        |3.0480:414273b3;
                        |4.5720:393264b3;
                        |1000.0:393264b3;""".stripMargin).get
                    floodTile.renderPng(breaks).bytes

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
