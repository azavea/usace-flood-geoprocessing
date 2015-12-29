package org.azavea.usaceflood.server

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.rasterize._
import geotrellis.raster.op.zonal.summary._
import geotrellis.spark.op.zonal.summary._

import scala.collection.mutable

import org.apache.spark._

object PercentageFlooding {
  class PercentageFloodingTileIntersectionHandler(min: Int, floodLevels: Seq[Double]) extends TileIntersectionHandler[(Map[Double, Long], Long)] {
    // Sort the flood levels in descending order so we can optimize checking flooded cells
    val fl = floodLevels.sorted.reverse.toArray
    val floodLevelCount = fl.length

    def handlePartialTile(raster: Raster, intersection: Polygon): (Map[Double, Long], Long) = {
      val tile = raster.tile

      val floodedCounts = mutable.Map[Double, Long]()
      var count = 0L

      Rasterizer.foreachCellByPolygon(intersection, raster.rasterExtent) { (col, row) =>
        val z = tile.getDouble(col, row)

        if(isData(z)) {
          count += 1
          var i = 0
          while(i < floodLevelCount) {
            val level = fl(i)
            if(z - min < level) {
              // This pixel is flooded under this level
              floodedCounts(level) += 1
            } else {
              // This pixel isn't flooded under this level,
              // also not flooded by any lower level, so break
              // the loop.
              i = floodLevelCount
            }
          }
        }
      }

      (floodedCounts.toMap, count)
    }

    def handleFullTile(tile: Tile): (Map[Double, Long], Long) = {
      val floodedCounts = mutable.Map[Double, Long]()
      var count = 0L
      tile.foreachDouble { z =>
        if(isData(z)) {
          count += 1
          var i = 0
          while(i < floodLevelCount) {
            val level = fl(i)
            if(z - min < level) {
              // This pixel is flooded under this level
              floodedCounts(level) += 1
            } else {
              // This pixel isn't flooded under this level,
              // also not flooded by any lower level, so break
              // the loop.
              i = floodLevelCount
            }
          }
        }
      }

      (floodedCounts.toMap, count)
    }

    def combineResults(values: Seq[(Map[Double, Long], Long)]): (Map[Double, Long], Long) = {
      val (merged, totalCount) = 
        values.foldLeft((Seq[(Double, Long)](), 0L)) { (acc, v) =>
          val (map, count) = v
          (acc._1 ++ map.toSeq, acc._2 + count)
        }

      val totalMap = 
        merged
          .groupBy(_._1)
          .map { case (key, value) => (key, value.map(_._2).sum) }
          .toMap

      (totalMap, totalCount)
    }
  }

  /** Takes a polygon and a sequence of flood levels (in meters).
    * Returns the percentage of the polygon that will be flooded under
    * each corresponding level.
    * 
    * @param       polygon       Polygon in EPSG:4269
    * @param       floodLevels   Flood levels in meters.
    * 
    * @return      A mapping between the flood levels and the percentages of the polygon covered in flood water.
    */
  def apply(polygon: Polygon, floodLevels: Seq[Double])(implicit sc: SparkContext): Map[Double, Double] = {
    val rdd = ElevationData(polygon)

    val (min, _) = rdd.minMax

    val (floodedCounts, totalCount) =
      rdd.zonalSummary(polygon, (Map[Double, Long](), 0L), new PercentageFloodingTileIntersectionHandler(min, floodLevels))

    floodLevels
      .map { level =>
        val percentage = 
          floodedCounts.get(level) match {
            case Some(floodedCount) =>
              floodedCount / totalCount.toDouble
            case None =>
              0.0
          }
        (level, percentage)
      }
      .toMap
  }
}
