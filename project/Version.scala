import scala.util.Properties

object Version {
  def either(environmentVariable: String, default: String): String =
    Properties.envOrElse(environmentVariable, default)

  val version = "0.1.5"
  val floodmodel = version + either("FLOODMODEL_VERSION_SUFFIX", "-SNAPSHOT")

  val scala = "2.11.8"
  val geotrellis = "1.0.0"

  val scalatest    = "2.2.1"
  lazy val jobserver = either("SPARK_JOBSERVER_VERSION", "0.5.1")
  lazy val hadoop  = either("SPARK_HADOOP_VERSION", "2.6.0")
  lazy val spark   = either("SPARK_VERSION", "2.1.0")

}
