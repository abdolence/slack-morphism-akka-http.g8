ThisBuild / organization := "$package$"

ThisBuild / scalaVersion := "$scalaver$"

ThisBuild / version := "0.0.1-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq( "-feature" )

ThisBuild / exportJars := true

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-language:higherKinds"
) ++ (CrossVersion.partialVersion( (ThisBuild / scalaVersion).value ) match {
  case Some( ( 2, n ) ) if n >= 13 => Seq( "-Xsource:2.14" )
  case Some( ( 2, n ) ) if n < 13  => Seq( "-Ypartial-unification" )
  case _                           => Seq()
})

ThisBuild / javacOptions ++= Seq(
  "-Xlint:deprecation",
  "-source",
  "1.8",
  "-target",
  "1.8",
  "-Xlint"
)

// Required dependencies
val slackMorphismVersion = "1.0.0"

// This template is for akka and akka-http as a primary framework
val akkaVersion = "2.5.27"
val akkaHttpVersion = "10.1.11"
val akkaHttpCirceVersion = "1.30.0"

// logging and configs for example
val logbackVersion = "1.2.3"
val scalaLoggingVersion = "3.9.2"
val scoptVersion = "3.7.1"

// To provide a ready to work example, we're using in this template embedded SwayDb to store tokens
// You should use more appropriate solutions depends on your requirements
val swayDbVersion = "0.11"

lazy val root =
  (project in file( "." )).settings(
    name := "$name$",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "org.latestbit" %% "slack-morphism" % slackMorphismVersion,
      "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceVersion
        excludeAll (ExclusionRule( organization = "com.typesafe.akka" ) ),
      "com.github.scopt" %% "scopt" % scoptVersion,
      "io.swaydb" %% "swaydb" % swayDbVersion
    )
  )
