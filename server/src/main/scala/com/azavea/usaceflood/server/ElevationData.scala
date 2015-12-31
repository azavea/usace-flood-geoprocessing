package com.azavea.usaceflood.server

import geotrellis.proj4.WebMercator
import geotrellis.vector._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.tiling._

import org.apache.spark._

object ElevationData {
  val localPath = "data/catalog"
  val path = s"file://${new java.io.File(localPath).getAbsolutePath}"

  private val wmLayoutScheme = ZoomedLayoutScheme(WebMercator, tileSize = 256)

  // Polygon is in EPSG:4269
  def apply(polygon: Polygon)(implicit sc: SparkContext): RasterRDD[SpatialKey] =
    HadoopLayerReader.spatial(path)
      .query(LayerId("usace-mississippi-dem-10m-epsg4269", 0))
      .where(Intersects(polygon.envelope))
      .toRDD

  /** Returns a tile if it intersects with this polygon (Web Mercator) */
  def apply(zoom: Int, key: SpatialKey, polygon: Polygon)(implicit sc: SparkContext): Option[Tile] = {
    val transform = MapKeyTransform(WebMercator, wmLayoutScheme.levelForZoom(zoom))
    val extent = transform(key)
    if(extent.intersects(polygon)) {
      Some(apply(zoom, key))
    } else {
      None
    }
  }

  def apply(zoom: Int, key: SpatialKey)(implicit sc: SparkContext): Tile =
    HadoopTileReader[SpatialKey, Tile](path)
      .read(LayerId("usace-mississippi-dem-xyz", zoom))
      .read(key)

}
