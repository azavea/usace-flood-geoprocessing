package org.azavea.usaceflood.server

import geotrellis.vector._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop._

import org.apache.spark._

object ElevationData {
  val localPath = "data/catalog"
  val path = "file://{new java.io.File(localPath).getAbsolutePath}"

  // Polygon is in EPSG:4269
  def apply(polygon: Polygon)(implicit sc: SparkContext): RasterRDD[SpatialKey] =
    HadoopLayerReader.spatial(path)
      .query(LayerId("usace-mississippi-dem-10m-epsg4269", 0))
      .where(Intersects(polygon.envelope))
      .toRDD
}
