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

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.model.Bucket
import org.scalatest.Assertions.fail
import org.scalatest.BeforeAndAfterAll

/** Tests that mix this trait in will fail unless a file called AwsCredentials.properties is created in src/test/resources, or
  * environment variables AWS_ACCESS_KEY and AWS_SECRET_KEY are properly set. */
trait Fixtures { this: BeforeAndAfterAll =>
  val bucketName = "test" + new java.util.Date().getTime
  val bucket: Bucket = try {
      println(s"Creating bucket $bucketName")
      s3.createBucket(bucketName)
    } catch {
      case e: Exception =>
        val awsCredentials = s3.awsCredentials
        fail(s"Error creating bucket with accessKey=${awsCredentials.getAWSAccessKeyId} and secretKey=${awsCredentials.getAWSSecretKey}\n${e.getMessage}")
    }

  override def afterAll(): Unit = {
    s3.deleteBucket(bucketName)
  }

  override def beforeAll(): Unit = {}

  def maybeS3FromEnv: Option[S3] = for {
    accessKey <- Some(System.getenv("AWS_ACCESS_KEY")) if accessKey.nonEmpty
    secretKey <- Some(System.getenv("AWS_SECRET_KEY")) if secretKey.nonEmpty
  } yield {
    implicit val credentials = new BasicAWSCredentials(accessKey, secretKey)
    new S3()
  }

  def maybeS3FromFile: Option[S3] = for {
    s3File      <- Util.readS3File()
    credentials <- Util.getAuthentication(s3File.accountName)
  } yield {
    new S3()(credentials)
  }

  lazy implicit val s3: S3 = maybeS3FromEnv.getOrElse(
                               maybeS3FromFile.getOrElse(
                                 fail("No AWS credentials found in environment variables and no .s3 file was found in the working directory, or a parent directory.")))
  lazy implicit val iam: IAM = IAM(s3.awsCredentials)
  lazy implicit val sns: SNS = SNS(s3.awsCredentials)
}
