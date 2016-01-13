package com.azavea.usaceflood.server

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.op.local._
import geotrellis.spark._

import org.apache.spark._

object FloodTile {
  /** Takes a polygon, flood level, zoom level and tile coordinates.
    * Returns a tile with the flood levels of each cell.
    * 
    * @param       zoom          Zoom level used to get extent for clipping
    * @param       key           Spatial key used to get extent for clipping
    * @param       multiPolygon  MultiPolygon in EPSG:4269 to clip output to
    * @param       minElevation  Minimum elevation under this polygon
    * @param       floodLevel    Flood level to use for determining cell flooding
    * 
    * @return      Tile with flood level of each flooded cell, NoData if the cell is not flooded.
    */
  def apply(tile: Tile, zoom: Int, key: SpatialKey, multiPolygon: MultiPolygon, minElevation: Double, floodLevel: Double)(implicit sc: SparkContext): Tile = {
    val extent = ElevationData.getExtent(zoom, key)
    val maskedTile = tile.mask(extent, multiPolygon)

    maskedTile.mapDouble { z =>
      if (isData(z) && z - minElevation < floodLevel) {
        floodLevel - (z - minElevation)
      } else {
        Double.NaN
      }
    }
  }
}
