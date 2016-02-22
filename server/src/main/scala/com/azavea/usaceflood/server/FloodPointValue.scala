package com.azavea.usaceflood.server

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.spark._

import org.apache.spark._

object FloodPointValue {
  /**
    * Takes a polygon, flood level, zoom level and tile coordinates, and a point.
    * Returns the flood amount at that point.
    *
    * @param       tile          The tile to get data from
    * @param       extent        Extent to restrict search to
    * @param       point         Point in EPSG:4269 to get output for
    * @param       minElevation  Minimum elevation under this polygon
    * @param       floodLevel    Flood level to use for determining cell flooding
    *
    * @return      Flood level at that point
    */
  def apply(tile: Tile, extent: Extent, point: Point, minElevation: Double, floodLevel: Double)(implicit sc: SparkContext): Double = {
    val (col, row) = RasterExtent(extent, tile.cols, tile.rows).mapToGrid(point.x, point.y)
    val z = tile.getDouble(col, row)

    if (isData(z) && z - minElevation < floodLevel) {
      floodLevel - (z - minElevation)
    } else {
      Double.NaN
    }
  }
}
