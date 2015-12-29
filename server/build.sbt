name := "usaceflood-server"

fork := true

// raise memory limits here if necessary
javaOptions += "-Xmx2G"
javaOptions += "-Djava.library.path=/usr/local/lib"

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis-spark" % Version.geotrellis,
  "com.azavea.geotrellis" %% "geotrellis-testkit" % Version.geotrellis % "test",
  "org.scalatest" %% "scalatest" % Version.scalatest % "test",
  "org.apache.spark" %% "spark-core" % Version.spark % "provided",
  "org.apache.hadoop" % "hadoop-client" % Version.hadoop % "provided",
  "spark.jobserver" %% "job-server-api" % Version.jobserver % "provided"
)

assemblyMergeStrategy in assembly := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case "META-INF/MANIFEST.MF" => MergeStrategy.discard
  case "META-INF\\MANIFEST.MF" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.RSA" => MergeStrategy.discard
  case "META-INF/ECLIPSEF.SF" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

net.virtualvoid.sbt.graph.Plugin.graphSettings
