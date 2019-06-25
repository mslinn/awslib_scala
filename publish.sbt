/* Copyright 2012-2016 Micronautics Research Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License. */

val vcs = "github"

// bintray settings
bintrayOrganization := Some("micronautics")
bintrayRepository := "scala"
bintrayPackageLabels := Seq("aws", "scala")
bintrayVcsUrl := Some(s"git@$vcs.com:mslinn/${ name.value }.git")

// sbt-site settings
//enablePlugins(SiteScaladocPlugin)
//siteSourceDirectory := target.value / "api"
//publishSite

// sbt-ghpages settings
//enablePlugins(GhpagesPlugin)
//git.remoteRepo := s"git@$vcs.com:mslinn/${ name.value }.git"

/*import java.nio.file.Path

doc in Compile ~= { (value: java.io.File) => // enhance doc command to also replace the CSS
  import java.nio.file.{Files, Paths, StandardCopyOption}
  val source: Path = Paths.get("src/site/latest/api/lib/template.css")
  val dest: Path = Paths.get("target/site/latest/api/lib/").resolve(source.getFileName)
  println(s"Copying $source to $dest")
  Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
  value
}

ghpagesPushSite ~= { _: Unit => // enhance doc command to also replace the CSS
  import java.nio.file.{Files, Paths, StandardCopyOption}
  val source: Path = Paths.get("src/site/latest/api/lib/template.css")
  val dest: Path = Paths.get("target/site/latest/api/lib/").resolve(source.getFileName)
  println(s"Copying $source to $dest")
  Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
  ()
}

previewSite ~= { _: Unit => // enhance doc command to also replace the CSS
  import java.nio.file.{Files, Paths, StandardCopyOption}
  val source: Path = Paths.get("src/site/latest/api/lib/template.css")
  val dest: Path = Paths.get("target/site/latest/api/lib/").resolve(source.getFileName)
  println(s"Copying $source to $dest")
  Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
  ()
}*/
