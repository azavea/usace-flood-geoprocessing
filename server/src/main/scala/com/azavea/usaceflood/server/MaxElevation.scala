package com.azavea.usaceflood.server

import geotrellis.vector._
import geotrellis.spark.op.zonal.summary._

import org.apache.spark._

object MaxElevation {
  /** Takes a polygon and returns the elevation of the highest cell
    * within the polygon.
    *
    * @param       multiPolygon  MultiPolygon in EPSG:4269
    *
    * @return      Elevation in meters
    */
  def apply(multiPolygon: MultiPolygon)(implicit sc: SparkContext): Double =
    ElevationData(multiPolygon).zonalMax(multiPolygon)
}
