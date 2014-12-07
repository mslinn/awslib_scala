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

import com.amazonaws.services.s3.model.Bucket
import org.scalatest.Assertions.fail
import org.scalatest.BeforeAndAfterAll

/** Tests that mix this trait in will fail unless a file called AwsCredentials.properties is created in src/test/resources, or
  * environment variables AWS_ACCESS_KEY and AWS_SECRET_KEY are properly set. */
trait Fixtures { this: Init with BeforeAndAfterAll =>
  val bucketName = s"www.test${new java.util.Date().getTime}.com"
  val bucket: Bucket = try {
      println(s"Creating bucket $bucketName")
      implicitly[S3].createBucket(bucketName)
    } catch {
      case e: Exception =>
        val awsCredentials = implicitly[S3].awsCredentials
        fail(s"Error creating bucket with accessKey=${awsCredentials.getAWSAccessKeyId} and secretKey=${awsCredentials.getAWSSecretKey}\n${e.getMessage}")
    }

  override def afterAll(): Unit = {
    bucket.delete()
  }

  override def beforeAll(): Unit = {}
}
