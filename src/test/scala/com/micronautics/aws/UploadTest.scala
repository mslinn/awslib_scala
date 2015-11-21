/* Copyright 2012-2015 Micronautics Research Corporation.
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

import com.amazonaws.services.s3.model.Bucket
import com.micronautics.aws.AclEnum._
import com.micronautics.aws.{Logger => _}
import org.apache.commons.io.FileUtils
import org.scalatest._

class UploadTest extends WordSpec with TestBase with IAMImplicits with S3Implicits {
  import java.net.URL
  import IAMTest._

  val Logger = org.slf4j.LoggerFactory.getLogger("UploadTest")
  implicit val iamClient = iam.iamClient

  val fileName = "uploadMe.png"
  val resourcesDir = "src/test/resources"

  val aKey = s"uploadDirectory/$fileName"
  val file = new File(resourcesDir, fileName)

  /** Download a binary file specified by the given bucket and key */
  def download(bucket: Bucket, key: String): Array[Byte] = {
    val url = bucket.resourceUrl(key).replace("https:", "http:") // step around SSL certificate mismatch
    val stream = new URL(url).openStream
    try {
      org.apache.commons.io.IOUtils.toByteArray(stream)
    } finally {
      stream.close()
    }
  }

  "uploadAssets" should {
    "sign, encode and upload files to AWS S3" in {
      Logger.info(s"Creating bucket $bucketName")
      BucketPolicy.createBucket(bucketName)

      Logger.info("Uploading asset")
      UploadPostV2(file, bucket, aKey, privateAcl)

      Logger.info("Uploading homework")
      UploadPostV2(file, bucket, aKey, publicAcl)

      val actual: Array[Byte] = download(bucket, aKey)
      val desired: Array[Byte] = FileUtils.readFileToByteArray(file)
      assert(actual === desired)
    }
  }
}

