package com.micronautics.aws

import scala.language.implicitConversions
import com.amazonaws.services.s3.model.S3ObjectSummary
import java.io.{File, FileWriter}
import java.nio.file.Path
import java.util.Date
import java.text.SimpleDateFormat
import grizzled.math.stats._
import java.util.ArrayList
import scala.collection.JavaConversions._
import java.nio.file.attribute.{BasicFileAttributeView, FileTime}
import java.nio.file.Files
import com.codahale.jerkson.Json._
import S3Model._
import io.Source
import org.joda.time.format.DateTimeFormat
import org.apache.commons.lang.SystemUtils.IS_OS_WINDOWS

/**
 * @author Mike Slinn */
object Util {
  var s3Option: Option[S3] = None

  lazy val dtFormat = DateTimeFormat.forPattern("HH:mm:ss 'on' mmm, dd YYYY")

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd 'at' hh:mm:ss z")

  def dtFmt(time: Long): String = dateFormat.format(new Date(time)).trim

  def dtFmt(date: Date): String = dateFormat.format(date).trim

  def latestFileTime(file: File): Long = {
    val fileAttributeView = Files.getFileAttributeView(file.toPath, classOf[BasicFileAttributeView])
    val creationTime = fileAttributeView.readAttributes().creationTime().toMillis
    val lastModifiedTime = fileAttributeView.readAttributes().lastModifiedTime().toMillis
    math.max(creationTime, lastModifiedTime)
  }

  /** Type erasure means that Java interop does not allow the parameters to be specified as ArrayList[Long] */
  def computeStats(modificationTimes: ArrayList[_], deletionTimes: ArrayList[_]): String = {
    val editResult = computeStatString("Edit time", modificationTimes.asInstanceOf[ArrayList[Long]])
    val deleteResult = computeStatString("Deletion time", deletionTimes.asInstanceOf[ArrayList[Long]])
    if (editResult.length>0 && deleteResult.length>0)
      return editResult + "\n" + deleteResult

    if (editResult.length>0)
      return editResult

    if (deleteResult.length>0)
      return deleteResult

    return ""
  }

  def computeStatString(label: String, values: ArrayList[Long]): String = {
    if (values.length==0)
      return ""

    if (values.length==1)
      return "1 value: " + values(0) + " ms";

    val millisMean = arithmeticMean(values: _*).asInstanceOf[Long]

    if (values.length<5)
      return "%s mean of %d values: %d ms".format(label, values.length, millisMean)

    // std deviation is +/- so subtract from mean and double it to show uncertainty range
    // midpoint of uncertainty is therefore the mean
    val stdDev = popStdDev(values: _*).asInstanceOf[Long]
    val result = "%s mean of %d values: %d ms, +/- %d ms (1 std dev: %d ms, 2 std devs: %d ms)".
      format(label, values.length, millisMean, stdDev, millisMean + stdDev, millisMean + 2*stdDev)
    return result
  }

  /** @return -2 if s3File does not exist,
   *          -1 if s3File is older than local copy,
   *           0 if same age as local copy,
   *           1 if remote copy is newer,
   *           2 if local copy does not exist */
  def compareS3FileAge(file: File, node: S3ObjectSummary): Int = {
    if (!file.exists)
      return s3FileDoesNotExistLocally

    if (null==node)
      return s3FileDoesNotExist

    // Some OSes only truncate lastModified time to the nearest second, so truncate both times to nearest second
    val s3NodeLastModified: Long = node.getLastModified.getTime / 1000L
    val fileTime: Long = (latestFileTime(file) + 500L) / 1000L // round to nearest second
    //println("s3NodeLastModified=" + s3NodeLastModified + "; lastestFileTime=" + fileLastModified)
    val result: Int = if (s3NodeLastModified == fileTime)
        s3FileSameAgeAsLocal
      else if (s3NodeLastModified < fileTime)
        s3FileIsOlderThanLocal
      else
        s3FileNewerThanLocal
      result
    }

  /** @return `.aws` contents as a String */
  def credentialFileContents: Option[String] = {
    if (credentialPath.toFile.exists)
      Some(Source.fromFile(credentialPath.toFile).mkString)
    else
      None
  }

  def credentialPath: Path = new File(sys.env("HOME"), ".aws").toPath

  def readS3File(): S3File = {
    findS3File() match {
      case None =>
        null

      case Some(file) =>
        parseS3File(file)
    }
  }

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

      case Some(credentials) =>
        Some(new S3(credentials.accessKey, credentials.secretKey))
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

      case Some(contents) =>
        val creds = AllCredentials(parse[Array[Credentials]](contents)).flatMap { cred =>
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

      case Some(credentials) =>
        val s3 = new S3(credentials.accessKey, credentials.secretKey)
        s3Option = Some(s3)
    }
    s3Option
  }

  /** @return S3File from parsing JSON in given `.s3` file */
  def parseS3File(file: File): S3File = try {
    parse[S3File](Source.fromFile(file, "UTF-8"))
  } catch {
    case e: Throwable =>
      println("Error parsing " + file.getAbsolutePath + ":\n" + e.getMessage)
      sys.exit(-2)
      null
  }

  def using[A <: {def close(): Unit}, B](param: A)(f: A => B): B = {
    import scala.language.reflectiveCalls
    try { f(param) } finally { param.close() }
  }

  def writeToFile(fileName:String, data:String) = 
    using (new FileWriter(fileName)) {
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
    val synced = newS3File.lastSyncOption match {
      case None =>
        "never synced"

      case Some(dateTime) =>
        "last synced " + dtFormat.print(dateTime)
    }
    writeS3(generate(newS3File) + "\n")
    synced
  }
}
