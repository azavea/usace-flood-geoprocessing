package com.azavea.usaceflood.ingest

import geotrellis.raster._
import geotrellis.raster.resample._
import geotrellis.spark._
import geotrellis.spark.utils._
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.io.index._
import geotrellis.spark.tiling._
import geotrellis.spark.ingest._
import geotrellis.proj4._

import org.apache.spark._
import org.apache.spark.rdd._

import scala.reflect.ClassTag
import java.io.File

trait Ingest {

  def withSparkContext(f: SparkContext => Unit) = {
    val sc = SparkUtils.createSparkContext(s"FloodModel ETL")
    try {
      f(sc)
    } finally {
      sc.stop()
    }
  }

  def buildPyramid[K: SpatialComponent: ClassTag](rdd: RasterRDD[K], zoom: Int, layoutScheme: LayoutScheme)(sink: (RasterRDD[K], Int) => Unit): List[(Int, RasterRDD[K])] = {
      if (zoom >= 1) {
        sink(rdd, zoom)
        val pyramidLevel @ (nextZoom, nextRdd) = Pyramid.up(rdd, layoutScheme, zoom)
        pyramidLevel :: buildPyramid(nextRdd, nextZoom, layoutScheme)(sink)
      } else {
        sink(rdd, zoom)
        List((zoom, rdd))
      }
    }
}

// Ingest floating layout in original projection
object BaseLayerIngest extends Ingest {
  // Assumes tiles are in EPSG:4269
  def main(args: Array[String]): Unit = {
    require(args.length == 2, "Usage: BaseLayerIngest INPUT_GEOTIFF_PATH OUTPUT_CATALOG_PATH")

    val layoutScheme = FloatingLayoutScheme(tileSize = 512)

    val inputPath = s"file://${new File(args(0)).getAbsolutePath}"
    val outputPath = s"file://${new File(args(1)).getAbsolutePath}"

    withSparkContext { implicit sc =>
      val sourceTiles = sc.hadoopGeoTiffRDD(inputPath)
 
      val (zoom, rasterMetaData) =
        RasterMetaData.fromRdd(sourceTiles, CRS.fromEpsgCode(4269), layoutScheme)(_.extent)

      val tiled: RDD[(SpatialKey, Tile)] = Tiler(sourceTiles, rasterMetaData, Bilinear)

      val rasterRDD = new RasterRDD(tiled, rasterMetaData)

      HadoopLayerWriter.spatial(outputPath, ZCurveKeyIndexMethod).write(LayerId("usace-mississippi-dem-10m-epsg4269", zoom), rasterRDD)
    }
  }
}

object ZoomedLayerIngest extends Ingest{
  // Assumes tiles are in EPSG:3857
  def main(args: Array[String]): Unit = {
    require(args.length == 2, "Usage: ZoomedLayerIngest INPUT_GEOTIFF_PATH OUTPUT_CATALOG_PATH")

    val layoutScheme = ZoomedLayoutScheme(WebMercator, tileSize = 512)

    val inputPath = s"file://${new File(args(0)).getAbsolutePath}"
    val outputPath = s"file://${new File(args(1)).getAbsolutePath}"

    withSparkContext { implicit sc =>
      val sourceTiles = sc.hadoopGeoTiffRDD(inputPath)
      val (zoom, rasterMetaData) =
        RasterMetaData.fromRdd(sourceTiles, WebMercator, layoutScheme)(_.extent)

      val tiled: RDD[(SpatialKey, Tile)] = Tiler(sourceTiles, rasterMetaData, Bilinear)

      val rasterRDD = new RasterRDD(tiled, rasterMetaData)

      val writer = HadoopLayerWriter.spatial(outputPath, ZCurveKeyIndexMethod)

      buildPyramid(rasterRDD, zoom, layoutScheme) { (rdd, z) => 
        val layerId = LayerId("usace-mississippi-dem-xyz", z)
        writer.write(layerId, rdd)
      }
    }
  }
}
