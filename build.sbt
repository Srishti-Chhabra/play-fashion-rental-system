name := """play-scala-seed"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.12"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.0" % Test
libraryDependencies += "org.mindrot" % "jbcrypt" % "0.4"
libraryDependencies += filters
libraryDependencies += "org.playframework" %% "play-slick" % "6.0.0-M2"
libraryDependencies += "org.playframework" %% "play-slick-evolutions" % "6.0.0-M2"
libraryDependencies += "com.h2database" % "h2" % "2.2.224"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.6.17"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.6.17"
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.2.6"
//libraryDependencies ++= Seq(evolutions, jdbc)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.example.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.example.binders._"
