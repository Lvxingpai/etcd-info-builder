name := """appconfig"""

organization := "com.lvxingpai"

version := "1.0"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.2.1",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.5.2",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.5.3"
)
