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

  val LIGHTEST_COLOR = 0xB5D1E8 // Pantone 277
  val DARKEST_COLOR = 0x002B7F  // Pantone 280
  val MAIN_ALPHA = 0xAB  // 66% visible
  val FINAL_ALPHA = 0xB3 // 70% visible
  val FINAL_BREAK = 500.0

  def cors: Directive0 = {
    val rh = implicitly[RejectionHandler]
    respondWithHeaders(corsHeaders) & handleRejections(rh)
  }

  def root =
    path("ping") { complete { "OK" } } ~
    pathPrefix("elevation") { elevationRoute } ~
    pathPrefix("flood-percentages") { floodPercentagesRoute } ~
    pathPrefix("flood-tiles") { floodTilesRoute } ~
    pathPrefix("flood-value") { floodValueRoute }

  def elevationRoute =
    cors {
      import spray.json.DefaultJsonProtocol._

      entity(as[elevationArgs]) { (args) =>
        complete {
          future {
            val multiPolygon = args.multiPolygon.toString().parseGeoJson[MultiPolygon].reproject(LatLng, nativeCRS)
            JsObject(
              "maxElevation" -> JsNumber(MaxElevation(multiPolygon)),
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
                  val breaks = getColorBreaksForRange(args.maxElevation, args.minElevation, 7)
                  floodTile.renderPng(breaks).bytes

                case None =>
                  ArrayTile.empty(TypeByte, 256, 256).renderPng().bytes
              }
            }
          }
        }
      }
    }

  def floodValueRoute =
    pathPrefix(IntNumber / IntNumber / IntNumber) { (zoom, x, y) =>
      import spray.json.DefaultJsonProtocol._

      entity(as[floodValueArgs]) { (args) =>
        complete {
          future {
            val key = SpatialKey(x, y)
            val point = Point(args.lng, args.lat).reproject(LatLng, WebMercator)
            val tile = ElevationData(zoom, key)
            val extent = ElevationData.getExtent(zoom, key)

            JsObject(
              "floodValue" -> JsNumber(FloodPointValue(tile, extent, point, args.minElevation, args.floodLevel))
            )
          }
        }
      }
    }

  def getColorBreaksForRange(maxElevation: Double, minElevation: Double, ticks: Int): ColorBreaks = {
    val tick = (maxElevation - minElevation) / ticks

    // Calculate main breaks, and add a really large one at the end
    val main_breaks = (1 to ticks).toArray.map(_ * tick)
    val breaks = main_breaks :+ FINAL_BREAK

    // Calculate main colors, and add the darkest color at the end
    val main_colors = ColorRamp.createWithRGBColors(LIGHTEST_COLOR, DARKEST_COLOR).setAlpha(MAIN_ALPHA).interpolate(ticks).toArray
    val final_color = ColorRamp.createWithRGBColors(DARKEST_COLOR).setAlpha(FINAL_ALPHA).toArray
    val colors = main_colors ++ final_color

    ColorBreaks(breaks, colors)
  }
}
