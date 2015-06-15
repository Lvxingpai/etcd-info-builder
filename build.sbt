name := """appconfig"""

organization := "com.lvxingpai"

version := "0.1.2"

crossScalaVersions := Seq("2.10.4", "2.11.4")

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.2.1",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.5.2",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.5.3"
)

scalacOptions ++= Seq("-feature", "-deprecation")

publishTo := {
  val nexus = "http://nexus.lvxingpai.com/content/repositories/"
  if (isSnapshot.value)
    Some("publishSnapshots" at nexus + "snapshots")
  else
    Some("publishReleases"  at nexus + "releases")
}
