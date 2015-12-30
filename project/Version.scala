import scala.util.Properties

object Version {
  def either(environmentVariable: String, default: String): String =
    Properties.envOrElse(environmentVariable, default)

  val floodmodel = "0.1.0" + either("FLOODMODEL_VERSION_SUFFIX", "-SNAPSHOT")

  val scala = "2.10.6"
  val geotrellis = "0.10.0-561030e"

  val scalatest    = "2.2.1"
  lazy val jobserver = either("SPARK_JOBSERVER_VERSION", "0.5.1")
  lazy val hadoop  = either("SPARK_HADOOP_VERSION", "2.6.0")
  lazy val spark   = either("SPARK_VERSION", "1.3.1")

}
