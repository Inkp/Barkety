organization := "us.troutwine"

name := "barkety"

version := "3.2.0"

scalaVersion := "2.11.6"

scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers += "Glassfish" at "http://maven.glassfish.org/content/repositories/maven.hudson-labs.org"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.4",
  "org.mockito" % "mockito-core" % "2.0.13-beta"
  ) map { _ % "test" }

libraryDependencies ++= Seq("actor") map { "com.typesafe.akka" %% "akka-%s".format(_) % "2.3.11" }

libraryDependencies ++= Seq("testkit") map { "com.typesafe.akka" %% "akka-%s".format(_) % "2.3.11" % "test" }

libraryDependencies ++= Seq("smack-java7", "smack-tcp", "smack-extensions") map { "org.igniterealtime.smack" % _ % "4.1.1" }

libraryDependencies ++= Seq("com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2")

initialCommands := """
import akka.actor._
import akka.actor.Actor._
import us.troutwine.barkety._
"""

//* vim: set filetype=scala : */
