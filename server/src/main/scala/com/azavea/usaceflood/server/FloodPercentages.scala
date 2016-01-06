package com.azavea.usaceflood.server

import geotrellis.vector._
import geotrellis.raster._
import geotrellis.raster.rasterize._
import geotrellis.raster.op.zonal.summary._
import geotrellis.spark._
import geotrellis.spark.op.zonal.summary._

import scala.collection.mutable

import org.apache.spark._

object FloodPercentages {
  /** Takes a multiPolygon and a sequence of flood levels (in meters).
    * Returns the percentage of the multiPolygon that will be flooded under
    * each corresponding level.
    * 
    * @param       multiPolygon  MultiPolygon in EPSG:4269
    * @param       floodLevels   Flood levels in meters.
    * 
    * @return      A mapping between the flood levels and the percentages of the polygon covered in flood water.
    */
  def apply(multiPolygon: MultiPolygon, floodLevels: Seq[Double])(implicit sc: SparkContext): Map[String, Double] = {
    val rdd = ElevationData(multiPolygon)
    val (minElevation, _) = rdd.minMax
    calculate(rdd, multiPolygon, floodLevels, minElevation)
  }

  /** Takes a polygon and a sequence of flood levels (in meters).
    * Returns the percentage of the polygon that will be flooded under
    * each corresponding level.
    * 
    * @param       multiPolygon  MultiPolygon in EPSG:4269
    * @param       floodLevels   Flood levels in meters.
    * @param       minElevation  The minimum elevation of the cells contained within this polygon.
    * 
    * @return      A mapping between the flood levels and the percentages of the polygon covered in flood water.
    */
  def apply(multiPolygon: MultiPolygon, floodLevels: Seq[Double], minElevation: Double)(implicit sc: SparkContext): Map[String, Double] = {
    val rdd = ElevationData(multiPolygon)
    calculate(rdd, multiPolygon, floodLevels, minElevation)
  }

  private def calculate(rdd: RasterRDD[SpatialKey], multiPolygon: MultiPolygon, floodLevels: Seq[Double], minElevation: Double)(implicit sc: SparkContext): Map[String, Double] = {
    val rdd = ElevationData(multiPolygon)

    val (floodedCounts, totalCount) =
      rdd.zonalSummary(multiPolygon, (Map[Double, Long](), 0L), new FloodPercentagesTileIntersectionHandler(minElevation, floodLevels))

    floodLevels
      .map { level =>
        val percentage = 
          floodedCounts.get(level) match {
            case Some(floodedCount) =>
              floodedCount / totalCount.toDouble
            case None =>
              0.0
          }
        (level.toString, percentage)
      }
      .toMap
  }

  class FloodPercentagesTileIntersectionHandler(minElevation: Double, floodLevels: Seq[Double]) extends TileIntersectionHandler[(Map[Double, Long], Long)] {
    // Sort the flood levels in descending order so we can optimize checking flooded cells
    val fl = floodLevels.sorted.reverse.toArray
    val floodLevelCount = fl.length

    def handlePartialTile(raster: Raster, intersection: Polygon): (Map[Double, Long], Long) = {
      val tile = raster.tile

      val floodedCounts = mutable.Map[Double, Long]().withDefaultValue(0)
      var count = 0L

      Rasterizer.foreachCellByPolygon(intersection, raster.rasterExtent) { (col, row) =>
        val z = tile.getDouble(col, row)

        if(isData(z)) {
          count += 1
          var i = 0
          while(i < floodLevelCount) {
            val level = fl(i)
            if(z - minElevation < level) {
              // This pixel is flooded under this level
              floodedCounts(level) += 1
              i += 1
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
      val floodedCounts = mutable.Map[Double, Long]().withDefaultValue(0)
      var count = 0L
      tile.foreachDouble { z =>
        if(isData(z)) {
          count += 1
          var i = 0
          while(i < floodLevelCount) {
            val level = fl(i)
            if(z - minElevation < level) {
              // This pixel is flooded under this level
              floodedCounts(level) += 1
              i += 1
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

      (totalMap, totalCount)
    }
  }
}
