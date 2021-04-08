/* Copyright 2012-2019 Micronautics Research Corporation. */

import sbt.Keys._
import Settings._

crossScalaVersions := Seq("2.13.2")

fork in Test := false

// Define the statements initially evaluated when entering 'console', 'console-quick', or 'console-project'
initialCommands := """
                     |""".stripMargin

javacOptions ++= Seq(
  "-Xlint:deprecation",
  "-Xlint:unchecked",
  "-g:vars"
)

resolvers ++= Seq(
  "micronautics/scala on bintray" at "https://dl.bintray.com/micronautics/scala"
)

libraryDependencies ++= {
  val httpV = "4.5.12"
  val jackV = "2.9.9"
  Seq(
    "ch.qos.logback"             %  "logback-classic"     % "1.2.3"   withSources(),
    "com.amazonaws"              %  "aws-java-sdk-osgi"   % "1.11.737" withSources(),
    "com.fasterxml.jackson.core" %  "jackson-annotations" % jackV      withSources() force(),
    "com.fasterxml.jackson.core" %  "jackson-core"        % jackV      withSources() force(),
    "com.fasterxml.jackson.core" %  "jackson-databind"    % jackV      withSources(),
    "com.google.code.findbugs"   %  "jsr305"              % "3.0.2"    withSources() force(),
    "com.micronautics"           %% "scalacourses-utils"  % "0.3.5"    withSources(),
    "com.typesafe"               %  "config"              % "1.4.0"    withSources() force(),
    "commons-codec"              %  "commons-codec"       % "1.14"     withSources() force(),
    "commons-io"                 %  "commons-io"          % "2.6"      withSources(),
    "commons-lang"               %  "commons-lang"        % "2.6"      withSources(),
    "org.apache.httpcomponents"  %  "httpclient"          % httpV      withSources() force(),
    "org.apache.httpcomponents"  %  "httpcore"            % "4.4.13"   withSources() force(),
    "org.apache.httpcomponents"  %  "httpmime"            % httpV      withSources() force(),
    "org.awaitility"             %  "awaitility"          % "4.0.2"    withSources(),
    "org.codehaus.jackson"       %  "jackson-mapper-asl"  % "1.9.13"   withSources(),
    "org.joda"                   %  "joda-convert"        % "2.2.1"    withSources() force(),
    "org.slf4j"                  %  "slf4j-api"           % "1.7.30"   withSources() force(),
    //
    "junit"                      %  "junit"               % "4.13"     % Test
  )
}

libraryDependencies ++= scalaVersion {
  case sv if sv.startsWith("2.13") => // Builds with Scala 2.13.x, Play 2.8.x
    val playV = "2.8.8"
    Seq(
      "com.typesafe.play"        %% "play-json"          % playV    withSources() force(),
      //
      "com.typesafe.play"        %% "play"               % playV    % Test withSources(),
      "org.scalatestplus.play"   %% "scalatestplus-play" % "5.0.0"  % Test withSources()
    )

  case sv if sv.startsWith("2.12") => // Builds with Scala 2.12.x, Play 2.8.x
    val playV = "2.8.8"
    Seq(
      "com.typesafe.play"        %% "play-json"               % playV    withSources() force(),
      "org.scala-lang.modules"   %% "scala-collection-compat" % "2.1.3"  withSources(),
      //
      "com.typesafe.play"        %% "play"                    % playV    % Test withSources(),
      "org.scalatestplus.play"   %% "scalatestplus-play"      % "5.0.0"  % Test withSources()
    )
}.value

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

logBuffered in Test := false

//logLevel := Level.Error

// Only show warnings and errors on the screen for compilations.
// This applies to both test:compile and compile and is Info by default
//logLevel in compile := Level.Warn

logLevel in test := Level.Info // Level.INFO is needed to see detailed output when running tests

name := "awslib_scala"

organization := "com.micronautics"

parallelExecution in Test := false

resolvers ++= Seq(
  "Typesafe Releases"             at "https://repo.typesafe.com/typesafe/releases",
  "micronautics/scala on bintray" at "https://dl.bintray.com/micronautics/scala"
)

scalacOptions ++= Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
  "-language:higherKinds",             // Allow higher-kinded types
  "-language:implicitConversions",     // Allow definition of implicit functions called views
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
)

scalacOptions ++=
  scalaVersion {
    case sv if sv.startsWith("2.13") => List(
      "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
      "-Xlint:constant"                    // Evaluation of a constant arithmetic expression results in an error.
    )

    case sv if sv.startsWith("2.12") => List(
      "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
      "-Xlint:constant",                    // Evaluation of a constant arithmetic expression results in an error.
      "-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
      "-Ypartial-unification",             // Enable partial unification in type constructor inference
      "-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
      "-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
      "-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
      "-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
      "-Ywarn-numeric-widen",               // Warn when numerics are widened.
      "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
      "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
      "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
      "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
      "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
      "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
      "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
      "-Xlint:option-implicit",            // Option.apply used implicit view.
      "-Xlint:package-object-classes",     // Class or object defined in package object.
      "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
      "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
      "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
      "-Xlint:type-parameter-shadow"      // A local type parameter shadows a type already in scope.
    )

    case _ => Nil
  }.value

// The REPL can’t cope with -Ywarn-unused:imports or -Xfatal-warnings so turn them off for the console
scalacOptions in (Compile, console) --= Seq("-Ywarn-unused:imports", "-Xfatal-warnings")

scalacOptions in (Compile, doc) ++= baseDirectory.map {
  bd: File => Seq[String](
     "-sourcepath", bd.getAbsolutePath,
     "-doc-source-url", s"https://github.com/$gitHubId/$name/tree/master€{FILE_PATH}.scala"
  )
}.value

scalaVersion := "2.13.2"

scmInfo := Some(
  ScmInfo(
    url(s"https://github.com/mslinn/$name"),
    s"git@github.com:mslinn/$name.git"
  )
)

ThisBuild / turbo := true

//updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)

version := "1.1.18"
