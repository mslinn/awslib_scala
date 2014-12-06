/* Copyright 2012-2014 Micronautics Research Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License. */

package com.micronautics.aws

import java.io.File

object S3Scala {
  /** Normal use case is to delete a directory and all its contents */
  def deletePrefix(s3: S3, bucketName: String, prefix: String): Unit =
    s3.allObjectData(bucketName, prefix).map { _.getKey } foreach { objName =>
      s3.deleteObject(bucketName, objName)
    }

  /** Recursive upload to AWS S3 bucket
    * @param file or directory to copy
    * @param dest path to copy to on AWS S3 */
  def uploadFileOrDirectory(s3: S3, bucketName: String, dest: String, file: File): Unit = {
    val newDest = if (dest=="") file.getName else s"$dest/${file.getName}"
    assert(!newDest.startsWith("/")) // verify this is a relative path
    if (file.isDirectory)
      file.listFiles.toSeq.foreach { file2 => uploadFileOrDirectory(s3, bucketName, newDest, file2) }
    else
      s3.uploadFile(bucketName, newDest, file)
    ()
  }
}
