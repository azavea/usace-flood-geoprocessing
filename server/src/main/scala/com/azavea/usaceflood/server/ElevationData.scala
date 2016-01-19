package com.azavea.usaceflood.server

import scala.util.Properties.envOrElse

import geotrellis.proj4.WebMercator
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.tiling._

import org.apache.spark._

object ElevationData {
  val localPath = envOrElse("RDD_CATALOG_PATH", "/flood-data/catalog")
  val path = s"file://${new java.io.File(localPath).getAbsolutePath}"
  val dem10mLayer = envOrElse("DEM_10M_LAYER", "usace-mississippi-dem-10m-epsg4269")
  val demXyzLayer = envOrElse("DEM_XYZ_LAYER", "usace-mississippi-dem-xyz")

  private val wmLayoutScheme = ZoomedLayoutScheme(WebMercator, tileSize = 256)

  def getExtent(zoom: Int, key: SpatialKey): Extent = {
    val transform = MapKeyTransform(WebMercator, wmLayoutScheme.levelForZoom(zoom))
    transform(key)
  }

  // MultiPolygon is in EPSG:4269
  def apply(multiPolygon: MultiPolygon)(implicit sc: SparkContext): RasterRDD[SpatialKey] =
    HadoopLayerReader.spatial(path)
      .query(LayerId(dem10mLayer, 0))
      .where(Intersects(multiPolygon.envelope))
      .toRDD

  /** Returns a tile if it intersects with this multiPolygon (Web Mercator) */
  def apply(zoom: Int, key: SpatialKey, multiPolygon: MultiPolygon)(implicit sc: SparkContext): Option[Tile] = {
    val extent = getExtent(zoom, key)
    if(extent.intersects(multiPolygon)) {
      Some(apply(zoom, key))
    } else {
      None
    }
  }

  def apply(zoom: Int, key: SpatialKey)(implicit sc: SparkContext): Tile =
    HadoopTileReader[SpatialKey, Tile](path)
      .read(LayerId(demXyzLayer, zoom))
      .read(key)

}
