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

import com.amazonaws.services.s3.model.Bucket
import org.scalatest.Assertions.fail
import org.scalatest.BeforeAndAfterAll

/** Tests that mix this trait in will fail unless a file called AwsCredentials.properties is created in src/test/resources, or
  * environment variables AWS_ACCESS_KEY and AWS_SECRET_KEY are properly set. */
trait Fixtures { this: BeforeAndAfterAll =>
  val bucketName = "test" + new java.util.Date().getTime
  var bucket: Bucket = null

  override def afterAll(): Unit = {
    s3.deleteBucket(bucketName)
    bucket = null
  }

  override def beforeAll(): Unit = try {
      println(s"Creating bucket $bucketName")
      bucket = s3.createBucket(bucketName)
    } catch {
      case e: Exception =>
        val creds = s3.awsCredentials
        fail(s"Error creating bucket with accessKey=${creds.getAWSAccessKeyId} and secretKey=${creds.getAWSSecretKey}\n${e.getMessage}")
    }

  def maybeS3FromEnv: Option[S3] = for {
    accessKey <- Some(System.getenv("AWS_ACCESS_KEY")) if accessKey.nonEmpty
    secretKey <- Some(System.getenv("AWS_SECRET_KEY")) if secretKey.nonEmpty
  } yield {
    new S3(accessKey, secretKey)
  }

  def maybeS3FromFile: Option[S3] = for {
    s3File      <- Util.readS3File()
    credentials <- Util.getAuthentication(s3File.accountName)
  } yield {
    S3Model.credentials = credentials
    new S3(credentials.accessKey, credentials.secretKey)
  }

  lazy val s3: S3 = maybeS3FromEnv.getOrElse(
                      maybeS3FromFile.getOrElse(
                        fail("No AWS credentials found. Is a .s3 file available in the working directory, or a parent directory?")))
}
