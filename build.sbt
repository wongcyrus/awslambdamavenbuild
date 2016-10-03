name := "CiMarking"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.4",
  "com.amazonaws" % "aws-java-sdk-core" % "1.11.34",
  "com.amazonaws" % "aws-lambda-java-events" % "1.3.0",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.34",
  "com.amazonaws" % "aws-java-sdk-ses" % "1.11.34",
  "commons-io" % "commons-io" % "2.4",
  "org.apache.commons" % "commons-compress" % "1.12"
)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}