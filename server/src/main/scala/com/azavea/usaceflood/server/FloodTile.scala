package org.azavea.usaceflood.server

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.spark._

import scala.collection.mutable

import org.apache.spark._

object FloodTile {
  /** Takes a polygon, flood level, zoom level and tile coordinates.
    * Returns a tile with the flood levels of each cell.
    * 
    * @param       polygon       Polygon in EPSG:4269
    * @param       minElevation  Minimum elevation under this polygon
    * @param       minElevation  Flood level to use for determing cell flooding
    * 
    * @return      Tile with flood level of each flooded cell, NoData if the cell is not flooded.
    */
  def apply(tile: Tile, polygon: Polygon, minElevation: Double, floodLevel: Double)(implicit sc: SparkContext): Tile =
    tile.mapDouble { z =>
      if(isData(z) && z - minElevation < floodLevel) {
        z - minElevation
      } else {
        Double.NaN
      }
    }
}

