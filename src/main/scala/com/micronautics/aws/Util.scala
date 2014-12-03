/* Copyright 2012-2014 Micronautics Research Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License. */

package com.micronautics.aws

import com.amazonaws.services.s3.AmazonS3Client

import collection.JavaConverters._
import com.amazonaws.services.s3.model.S3ObjectSummary
import grizzled.math.stats._
import java.io.{File, FileWriter}
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.{ArrayList=>JArrayList}
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.Files
import language.implicitConversions
import org.apache.commons.lang.SystemUtils.IS_OS_WINDOWS
import org.joda.time.format.DateTimeFormat
import play.api.libs.json._
import S3Model._

object Util {
  implicit val s3FileFormat = Json.format[S3File]

  var s3Option: Option[S3] = None

  lazy val dtFormat = DateTimeFormat.forPattern("HH:mm:ss 'on' mmm, dd YYYY")

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd 'at' hh:mm:ss z")

  def dtFmt(time: Long): String = dateFormat.format(new Date(time)).trim

  def dtFmt(date: Date): String = dateFormat.format(date).trim

  def latestFileTime(file: File): Long = {
    val fileAttributeView = Files.getFileAttributeView(file.toPath, classOf[BasicFileAttributeView])
    val creationTime = fileAttributeView.readAttributes.creationTime.toMillis
    val lastModifiedTime = fileAttributeView.readAttributes.lastModifiedTime.toMillis
    math.max(creationTime, lastModifiedTime)
  }

  /** Type erasure means that Java interop does not allow the parameters to be specified as ArrayList[Long] */
  def computeStats(modificationTimes: JArrayList[Long], deletionTimes: JArrayList[Long]): String = {
    val editResult = computeStatString("Edit time", modificationTimes)
    val deleteResult = computeStatString("Deletion time", deletionTimes)
    if (editResult.length>0 && deleteResult.length>0) s"$editResult\n$deleteResult"
    else if (editResult.length>0) editResult
    else if (deleteResult.length>0) deleteResult
    else ""
  }

  def computeStatString(label: String, values: JArrayList[Long]): String = values.size match {
    case 0 => ""
    case 1 => s"1 value: ${values.get(0)}ms"
    case _ =>
      val millisMean: Long = arithmeticMean(values.asScala: _*).toLong
      if (values.size<5) {
        s"$label mean of ${values.size} values: $millisMean ms"
      } else {
        // Std deviation is +/- so subtract from mean and double it to show uncertainty range. Midpoint of uncertainty is the mean.
        val stdDev: Long = popStdDev(values.asScala: _*).toLong
        s"$label mean of ${values.size}} values: $millisMean ms, +/- $stdDev ms (1 std dev: ${millisMean+stdDev} ms, 2 std devs: %${millisMean + 2*stdDev}} ms)."
      }
  }

  /** @return -2 if s3File does not exist,
   *          -1 if s3File is older than local copy,
   *           0 if same age as local copy,
   *           1 if remote copy is newer,
   *           2 if local copy does not exist */
  def compareS3FileAge(file: File, node: S3ObjectSummary): Int = {
    if (!file.exists) s3FileDoesNotExistLocally
    else if (null==node) s3FileDoesNotExist
    else { // Some OSes only truncate lastModified time to the nearest second, so truncate both times to nearest second
      val s3NodeLastModified: Long = node.getLastModified.getTime / 1000L
      val fileTime: Long = (latestFileTime(file) + 500L) / 1000L // round to nearest second
      //println(s"s3NodeLastModified=$s3NodeLastModified; lastestFileTime=$fileLastModified")
      if (s3NodeLastModified == fileTime) s3FileSameAgeAsLocal
        else if (s3NodeLastModified < fileTime) s3FileIsOlderThanLocal
        else s3FileNewerThanLocal
    }
  }

  /** @return `.aws` contents as a String */
  def credentialFileContents: Option[String] = {
    val file = credentialPath.toFile
    if (file.exists) Some(io.Source.fromFile(credentialPath.toFile).mkString)
    else None
  }

  def credentialPath: Path = new File(sys.env("HOME"), ".aws").toPath

  def readS3File(): Option[S3File] = findS3File() map { parseS3File }

  /**
    * Walk up from the current directory
    * @return Some(File) for first `.s3` file found, or None
    */
  def findS3File(fileName: String=".s3", directory: File=new File(System.getProperty("user.dir"))): Option[File] = {
    val file = new File(directory, fileName)
    if (file.exists()) {
      Some(file)
    } else {
      val parent = directory.getParent
      if (parent==null)
        None
      else
        findS3File(fileName, new File(parent))
    }
  }

  /** @return `Some(S3)` for any `.s3` file found, or None */
  def findS3: Option[S3] = findS3FileObject match {
    case None =>
      None

    case Some(s3File) =>
      s3Option(s3File.accountName)
  }

  def s3Option(accountName: String): Option[S3] = {
    getAuthentication(accountName) match {
      case None =>
        None

      case Some(creds) =>
        Some(S3(creds.asBasicAWSCredentials))
    }
  }

  /** @return `Some(S3File)` for any `.s3` file found, or None */
  def findS3FileObject: Option[S3File] = findS3File() match {
    case None =>
      None

    case Some(file) =>
      Some(parseS3File(file))
  }

  /**
    * Parse `.aws` file if it exists
    * @return Some(Credentials) authentication for given accountName, or None
    */
  def getAuthentication(accountName: String): Option[Credentials] = {
    credentialFileContents match {
      case None =>
        None

      case Some(contents) => None
        implicit val credsFormat = Json.format[Credentials]
        val credArray: Seq[Credentials] = Json.fromJson[Seq[Credentials]](Json.parse(contents)).get
        val creds = AllCredentials(credArray.toArray).flatMap { cred =>
          if (cred.awsAccountName==accountName)
            Some(cred)
          else
            None
        }
        if (creds.length==0)
          None
        else
          Some(creds.head)
    }
  }

  implicit def s3fileTos3Option(s3File: S3File): Option[S3] = {
    getAuthentication(s3File.accountName) match {
      case None =>
        s3Option = None

      case Some(creds) =>
        s3Option = Some(S3(creds.asBasicAWSCredentials))
    }
    s3Option
  }

  /** @return S3File from parsing JSON in given `.s3` file */
  def parseS3File(file: File): S3File = try {
    val str = io.Source.fromFile(file, "UTF-8").toString
    val jsValue: JsValue = Json.parse(str)
    Json.fromJson[S3File](jsValue).get
  } catch {
    case e: Exception =>
      println(s"Error parsing ${file.getAbsolutePath}:\n${e.getMessage}")
      throw(e)
  }

  private def withCloseable[A <: {def close(): Unit}, B](param: A)(f: A => B): B = {
    import scala.language.reflectiveCalls
    try { f(param) } finally { param.close() }
  }

  def writeToFile(fileName:String, data:String) =
    withCloseable (new FileWriter(fileName)) {
      fileWriter => fileWriter.write(data)
    }

  def writeS3(contents: String): Unit = {
    val s3File = new File(System.getProperty("user.dir"), ".s3")
    val s3Path = s3File.toPath
    writeToFile(s3File.getAbsolutePath, contents.replaceAll("(.*?:(\\[.*?\\],|.*?,))", "$0\n "))
    makeFileHiddenIfDos(s3Path)
  }

  def makeFileHiddenIfDos(path: Path): Unit = {
    if (IS_OS_WINDOWS) {
      val file = path.toFile
      if (file.exists && !file.isHidden) {
        Files.setAttribute(file.toPath, "dos:hidden", true)
        ()
      }
    }
  }

  /**
   * Write `.s3` file
   * @return String indicating when last synced, if ever synced
   */
  def writeS3(newS3File: S3File): String = {
    writeS3(Json.toJson(newS3File) + "\n")
    newS3File.lastSyncOption match {
      case None =>
        "never synced"

      case Some(dateTime) =>
        s"last synced ${dtFormat.print(dateTime)}"
    }
  }
}
