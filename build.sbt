organization := "com.typesafe.akka.samples"
name := "akka-sample-persistence-scala"

scalaVersion := "2.12.6"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence" % "2.5.16",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.90",
  "com.typesafe.akka" %% "akka-persistence-cassandra-launcher" % "0.90" % Test,
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
)

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
