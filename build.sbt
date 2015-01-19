import sbt.Keys._
import bintray.Keys._

version := "1.0.0"
name := "awslib_scala"
organization := "com.micronautics"
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
scalaVersion := "2.11.5"
crossScalaVersions := Seq("2.10.4", "2.11.5")

scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-target:jvm-1.7", "-unchecked",
    "-Ywarn-adapted-args", "-Ywarn-value-discard", "-Xlint")

javacOptions ++= Seq("-Xlint:deprecation", "-Xlint:unchecked", "-source", "1.8", "-target", "1.8", "-g:vars")

scalacOptions in (Compile, doc) <++= baseDirectory.map {
  (bd: File) => Seq[String](
     "-sourcepath", bd.getAbsolutePath,
     "-doc-source-url", "https://github.com/mslinn/awslib_scala/tree/master€{FILE_PATH}.scala"
  )
}

resolvers ++= Seq(
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"
)

libraryDependencies <++= scalaVersion { 
  case sv if sv.startsWith("2.11") => // Builds with Scala 2.11.x, Play 2.3.x
    Seq(
      "com.typesafe.play"    %% "play-json"           % "2.3.7"  withSources(),
      //
      "com.typesafe.play"    %% "play"                % "2.3.7" % "test" withSources(),
      "com.typesafe.play"    %% "play-ws"             % "2.3.7" % "test" withSources(),
      "org.scalatestplus"    %% "play"                % "1.2.0" % "test" withSources()
    )

  case sv if sv.startsWith("2.10") => // Builds with Scala 2.10.x, Play 2.2.x
    Seq(
      "com.typesafe.play"    %% "play-json"           % "2.2.6"  withSources(),
      //
      "com.typesafe.play"    %% "play"                % "2.2.6" % "test" withSources(),
      "org.scalatestplus"    %% "play"                % "1.0.0" % "test" withSources()
    )
}

libraryDependencies ++= Seq(
  "org.codehaus.jackson"   %  "jackson-mapper-asl"  % "1.9.13",
  "com.amazonaws"          %  "aws-java-sdk"        % "1.9.15",
  "commons-io"             %  "commons-io"          % "2.4"   withSources(),
  "commons-lang"           %  "commons-lang"        % "2.6"   withSources(),
  "org.clapper"            %  "grizzled-scala_2.10" % "1.2"   withSources(),
  "com.github.nscala-time" %  "nscala-time_2.10"    % "1.6.0" withSources(),
  "org.slf4j"              %  "slf4j-api"           % "1.7.5" withSources(),
  "ch.qos.logback"         %  "logback-classic"     % "1.1.2" withSources(),
  //
  "junit"                  %  "junit"               % "4.11"  % "test",
  "org.scalatest"          %% "scalatest"           % "2.2.1" % "test" withSources(),
  "org.scalautils"         %% "scalautils"          % "2.1.7" % "test" withSources()
)

bintrayPublishSettings
bintrayOrganization in bintray := Some("micronautics")
repository in bintray := "scala"

publishArtifact in Test := false

updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)
logBuffered in Test := false
Keys.fork in Test := false
parallelExecution in Test := false
//logLevel := Level.Error

// define the statements initially evaluated when entering 'console', 'console-quick', or 'console-project'
initialCommands := """
  |""".stripMargin

// Only show warnings and errors on the screen for compilations.
// This applies to both test:compile and compile and is Info by default
//logLevel in compile := Level.Warn
logLevel in test := Level.Info // Level.INFO is needed to see detailed output when running tests
