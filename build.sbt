lazy val commonSettings = Seq(
  version := Version.floodmodel,
  scalaVersion := Version.scala,
  description := "USACE Flood Model",
  organization := "com.azavea",
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-Yinline-warnings",
    "-language:implicitConversions",
    "-language:reflectiveCalls",
    "-language:higherKinds",
    "-language:postfixOps",
    "-language:existentials",
    "-feature"),

  resolvers += Resolver.bintrayRepo("azavea", "geotrellis"),

  shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
) ++ net.virtualvoid.sbt.graph.Plugin.graphSettings

lazy val root = 
  Project("usaceflood", file("."))
    .aggregate(ingest, server)

lazy val ingest = 
  Project("ingest", file("ingest"))
  .settings(commonSettings: _*)

lazy val server =
  Project("server", file("server"))
  .settings(commonSettings: _*)
