package com.micronautics.aws

import java.util
import com.amazonaws.services.s3.model.S3ObjectSummary
import collection.JavaConverters._

object S3Scala {

  /** Normal use case is to delete a directory and all its contents */
  def deletePrefix(s3: S3, bucketName: String, prefix: String): Unit = {
    val objs: util.LinkedList[S3ObjectSummary] = s3.getAllObjectData(bucketName, prefix)
    val objNames = objs.listIterator().asScala.toSeq.map { _.getKey }
    objNames.foreach { objName =>
      s3.deleteObject(bucketName, objName)
    }
  }

}