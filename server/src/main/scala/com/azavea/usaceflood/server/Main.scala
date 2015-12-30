package org.azavea.usaceflood.server

import akka.actor._
import akka.io.IO
import spray.can.Http

import org.apache.spark._

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system = akka.actor.ActorSystem("usaceflood-server")

    val conf =
      new SparkConf()
        .setAppName("USACE FloodModel Server")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.hadoop.KryoRegistrator")

    val sc = new SparkContext(conf)

    // create and start our service actor
    val service =
      system.actorOf(Props(classOf[FloodModelServiceActor], sc), "usaceflood")

    // start a new HTTP server on port 8090 with our service actor as the handler
    IO(Http) ! Http.Bind(service, "0.0.0.0", 8090)
  }
}
