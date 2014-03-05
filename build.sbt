//import com.typesafe.sbt.SbtStartScript

organization := "com.micronautics"

name := "awss3"

//crossPaths := false

version := "0.1.3-SNAPSHOT"

scalaVersion := "2.10.3"

scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-target:jvm-1.7", "-unchecked",
    "-Ywarn-adapted-args", "-Ywarn-value-discard", "-Xlint")

javacOptions ++= Seq("-Xlint:deprecation", "-Xlint:unchecked", "-source", "1.7", "-target", "1.7", "-g:vars")

scalacOptions in (Compile, doc) <++= baseDirectory.map {
  (bd: File) => Seq[String](
     "-sourcepath", bd.getAbsolutePath,
     "-doc-source-url", "https://github.com/mslinn/AwsS3/tree/master€{FILE_PATH}.scala"
  )
}

resolvers ++= Seq(
  "Typesafe Releases"   at "http://repo.typesafe.com/typesafe/releases"
)

libraryDependencies ++= Seq(
  "org.codehaus.jackson"          %  "jackson-mapper-asl"  % "1.9.11",
  "com.amazonaws"                 %  "aws-java-sdk"        % "1.7.2",
  "com.typesafe.play"             %% "play-json"           % "2.2.1",
  "commons-io"                    %  "commons-io"          % "2.4",
  "commons-lang"                  %  "commons-lang"        % "2.6",
  "org.clapper"                   %% "grizzled-scala"      % "1.1.4",
  "org.scala-tools"               %  "time"                % "2.7.4-0.1",
  //
  "junit"                         %  "junit"               % "4.11"  % "test",
  "org.scalatest"                 %% "scalatest"           % "2.0.M8" % "test"
)

publishTo := Some(Resolver.file("file", new File(Path.userHome.absolutePath + "/.ivy2/local")))

publishMavenStyle := true

//seq(SbtStartScript.startScriptForClassesSettings: _*)

//logLevel := Level.Error

// define the statements initially evaluated when entering 'console', 'console-quick', or 'console-project'
initialCommands := """
  """

// Only show warnings and errors on the screen for compilations.
// This applies to both test:compile and compile and is Info by default
//logLevel in compile := Level.Warn

