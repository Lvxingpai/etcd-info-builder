name := """etcd-store"""

organization := "com.lvxingpai"

version := "0.5.0-SNAPSHOT"

scalaVersion := "2.11.4"

crossScalaVersions := "2.10.4" :: "2.11.4" :: Nil

libraryDependencies ++= Seq(
  "com.lvxingpai" %% "configuration" % "0.1.2",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.6.3",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.3",
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, scalaMajor)) if scalaMajor >= 11 =>
      "net.ceedubs" %% "ficus" % "1.1.2"
    case _ =>
      "net.ceedubs" %% "ficus" % "1.0.1"
  }
)

scalacOptions ++= Seq("-feature", "-deprecation")

scalariformSettings
