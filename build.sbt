import sbt.Keys._

version := "0.2.0"
name := "awss3"
organization := "com.micronautics"
//crossPaths := false
scalaVersion := "2.10.4"

scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-target:jvm-1.7", "-unchecked",
    "-Ywarn-adapted-args", "-Ywarn-value-discard", "-Xlint")

javacOptions ++= Seq("-Xlint:deprecation", "-Xlint:unchecked", "-source", "1.8", "-target", "1.8", "-g:vars")

scalacOptions in (Compile, doc) <++= baseDirectory.map {
  (bd: File) => Seq[String](
     "-sourcepath", bd.getAbsolutePath,
     "-doc-source-url", "https://github.com/mslinn/AwsS3/tree/master€{FILE_PATH}.scala"
  )
}

resolvers ++= Seq(
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases"
)

libraryDependencies ++= Seq(
  "org.codehaus.jackson"          %  "jackson-mapper-asl"  % "1.9.13",
  "com.amazonaws"                 %  "aws-java-sdk"        % "1.9.9",
  "com.typesafe.play"             %% "play-json"           % "2.2.6" withSources(),
  "commons-io"                    %  "commons-io"          % "2.4"   withSources(),
  "commons-lang"                  %  "commons-lang"        % "2.6"   withSources(),
  "org.clapper"                   %  "grizzled-scala_2.10" % "1.2"   withSources(),
  "com.github.nscala-time"        %  "nscala-time_2.10"    % "1.6.0" withSources(),
  "org.slf4j"                     %  "slf4j-api"           % "1.7.5" withSources(),
  "ch.qos.logback"                %  "logback-classic"     % "1.1.2" withSources(),
  //
  "junit"                         %  "junit"               % "4.11"  % "test",
  "org.scalatest"                 %  "scalatest_2.10"      % "2.2.2" % "test"
)

updateOptions := updateOptions.value.withCachedResolution(true)
publishTo := Some(Resolver.file("file", new File(Path.userHome.absolutePath + "/.ivy2/local")))
publishMavenStyle := true
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
