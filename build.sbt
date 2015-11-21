import sbt.Keys._

version := "1.1.0"
name := "awslib_scala"
organization := "com.micronautics"
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
scalaVersion := "2.10.6"
crossScalaVersions := Seq("2.10.6", "2.11.7")

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
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases",
  "micronautics/scala on bintray" at "http://dl.bintray.com/micronautics/scala"
)

libraryDependencies ++= {
  val httpV = "4.4.1"
  Seq(
    "org.apache.httpcomponents" %  "httpclient"         % httpV    withSources() force(),
    "org.apache.httpcomponents" %  "httpcore"           % httpV    withSources() force(),
    "org.apache.httpcomponents" %  "httpmime"           % httpV    withSources() force(),
    "org.codehaus.jackson"      %  "jackson-mapper-asl" % "1.9.13",
    "com.amazonaws"             %  "aws-java-sdk-osgi"  % "1.10.35" withSources(),
    "com.micronautics"          %% "scalacourses-utils" % "0.2.11"  withSources(),
    "commons-io"                %  "commons-io"         % "2.4"     withSources(),
    "commons-lang"              %  "commons-lang"       % "2.6"     withSources(),
    "ch.qos.logback"            %  "logback-classic"    % "1.1.3"   withSources(),
    //
    "junit"                     %  "junit"              % "4.12"  % "test",
    "org.scalatest"             %% "scalatest"          % "2.2.5" % "test" withSources(),
    "org.scalautils"            %% "scalautils"         % "2.1.7" % "test" withSources()
  )
}

libraryDependencies <++= scalaVersion {
  case sv if sv.startsWith("2.11") => // Builds with Scala 2.11.x, Play 2.4.3
    val playV = "2.4.3"
    Seq(
      "com.typesafe.play"      %% "play-json"      % playV   withSources(),
      "com.github.nscala-time" %% "nscala-time"    % "2.4.0" withSources(),
      "org.clapper"            %% "grizzled-scala" % "1.3"   withSources(),
      //
      "com.typesafe.play"      %% "play"           % playV      % "test" withSources(),
      "com.typesafe.play"      %% "play-ws"        % playV      % "test" withSources(),
      "org.scalatestplus"      %% "play"           % "1.4.0-M4" % "test" withSources()
    )

  case sv if sv.startsWith("2.10") => // Builds with Scala 2.10.x, Play 2.2.6
    val playV = "2.2.6"
    Seq(
      "com.typesafe.play"      %% "play-json"           % playV   withSources(),
      //"com.github.nscala-time" %  "nscala-time_2.10"    % "2.2.0" withSources(),
      // temporary for JDK 1.8u60 bug:
      "com.github.nscala-time" %% "nscala-time"         % "2.4.0" exclude("joda-time", "joda-time"),
      "joda-time"              % "joda-time"            % "2.8.2",
      //
      "org.clapper"            %  "grizzled-scala_2.10" % "1.3"   withSources(),
      //
      "com.typesafe.play"      %% "play"                % playV      % "test" withSources(),
      "org.scalatestplus"      %% "play"                % "1.4.0-M4" % "test" withSources()
    )
}

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

bintrayOrganization := Some("micronautics")
bintrayRepository := "scala"
