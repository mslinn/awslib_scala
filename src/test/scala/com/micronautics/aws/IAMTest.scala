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

import com.amazonaws.auth.AWSCredentials
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpec}

class IAMTest extends WordSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll {
  import com.amazonaws.services.identitymanagement.model.{User=>IAMUser}
  import com.amazonaws.services.s3.model.Bucket
  import scala.util.{Success, Try}

  lazy implicit val awsCredentials: AWSCredentials = maybeCredentialsFromEnv.getOrElse(
                                                       maybeCredentialsFromFile.getOrElse(
                                                         sys.error("No AWS credentials found in environment variables and no .s3 file was found in the working directory, or a parent directory.")))
  lazy implicit val s3: S3 = new S3()
  lazy implicit val cf: CloudFront = new CloudFront()
  lazy implicit val et: ElasticTranscoder = new ElasticTranscoder()
  lazy implicit val iam: IAM = new IAM()
  lazy implicit val sns: SNS = new SNS()

  val bucketName = s"www.test${new java.util.Date().getTime}.com"
  var bucket: Bucket = try {
      println(s"Creating bucket $bucketName")
      implicitly[S3].createBucket(bucketName)
    } catch {
      case e: Exception =>
        val awsCredentials = implicitly[S3].awsCredentials
        fail(s"Error creating bucket with accessKey=${awsCredentials.getAWSAccessKeyId} and secretKey=${awsCredentials.getAWSSecretKey}\n${e.getMessage}")
    }

  val iamUser1Name = "iamUser1"
  val Success((iamUser1, iamUser1Creds)) = iam.createIAMUser(iamUser1Name)

  "IAMUsers" must {
    "be manipulable" in {
      import com.amazonaws.auth.policy.Statement
      val stmt1: Statement = IAM.allowAllStatement(bucket, List(iamUser1.principal), "This is a test")
      bucket.policy = List(stmt1)
      assert(bucket.policyAsJson==s"""{"Version":"2012-10-17","Statement":[{"Sid":"This is a test","Effect":"Allow","Principal":{"AWS":"arn:aws:iam::031372724784:user/iamUser1"},"Action":"s3:*","Resource":"arn:aws:s3:::$bucketName"}]}""")

      assert(iam.findUser(iamUser1Name).isDefined)
      iamUser1.deleteUser()
      assert(iam.findUser(iamUser1Name).isEmpty)
    }
  }
}
