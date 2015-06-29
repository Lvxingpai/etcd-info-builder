name := """appconfig"""

organization := "com.lvxingpai"

version := "0.2.1"

crossScalaVersions := "2.10.4" :: "2.11.4" :: Nil

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.2.1",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.5.2",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.5.3",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, scalaMajor)) if scalaMajor >= 11 =>
      "net.ceedubs" %% "ficus" % "1.1.2"
    case _ =>
      "net.ceedubs" %% "ficus" % "1.0.1"
  }
)

scalacOptions ++= Seq("-feature", "-deprecation")

publishTo := {
  val nexus = "http://nexus.lvxingpai.com/content/repositories/"
  if (isSnapshot.value)
    Some("publishSnapshots" at nexus + "snapshots")
  else
    Some("publishReleases" at nexus + "releases")
}
