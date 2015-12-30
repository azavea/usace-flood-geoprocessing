package org.azavea.usaceflood.server

import geotrellis.vector._

import org.apache.spark._

object MinElevation {
  /** Takes a polygon and returns the elevation of the lowest cell
    * within the polygon.
    * 
    * @param       polygon       Polygon in EPSG:4269
    * 
    * @return      Elevation in meters
    */
  def apply(polygon: Polygon)(implicit sc: SparkContext): Double =
    ElevationData(polygon).minMax._1
}
