package org.azavea.usaceflood.geoprocessing

import com.typesafe.config.Config
import org.apache.spark._
import org.apache.spark.SparkContext._

import spark.jobserver._

import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.avro.codecs._
import geotrellis.vector._

case class SummaryJobParams(nlcdLayerId: LayerId, soilLayerId: LayerId, geometry: Seq[MultiPolygon])

object SummaryJob extends SparkJob {

  override def validate(sc: SparkContext, config: Config): SparkJobValidation = {
    SparkJobValid
  }

  override def runJob(sc: SparkContext, config: Config): Any = {
    "{\"test\": \"success\"}"
  }
}
