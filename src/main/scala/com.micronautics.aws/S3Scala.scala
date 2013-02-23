package com.micronautics.aws

import java.util
import com.amazonaws.services.s3.model.S3ObjectSummary
import collection.JavaConverters._
import java.io.File

object S3Scala {

  /** Normal use case is to delete a directory and all its contents */
  def deletePrefix(s3: S3, bucketName: String, prefix: String): Unit = {
    val objs: util.LinkedList[S3ObjectSummary] = s3.getAllObjectData(bucketName, prefix)
    val objNames = objs.listIterator().asScala.toSeq.map { _.getKey }
    objNames.foreach { objName =>
      s3.deleteObject(bucketName, objName)
    }
  }

  /** Recursive upload to AWS S3 bucket
    * @param file or directory to copy
    * @param dest path to copy to on AWS S3 */
  def uploadFileOrDirectory(s3: S3, bucketName: String, dest: String, file: File): Unit = {
    val newDest = if (dest=="") file.getName else dest + "/" + file.getName
    println(newDest) // verify this is a relative path
    if (file.isDirectory) {
      file.listFiles.toSeq.foreach { file2 => uploadFileOrDirectory(s3, bucketName, newDest, file2) }
    } else
      s3.uploadFile(bucketName, newDest, file)
    ()
  }

}
