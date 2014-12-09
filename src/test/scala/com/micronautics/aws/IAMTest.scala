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

class IAMTest extends TestBase {
  import com.amazonaws.services.identitymanagement.model.{User=>IAMUser}
  import com.amazonaws.services.s3.model.Bucket
  import scala.util.Success

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

  val iamUser2Name = "iamUser2"
  val Success((iamUser2, iamUser2Creds)) = iam.createIAMUser(iamUser2Name)


  "IAMUsers" must {
    "be manipulable" in {
      import com.amazonaws.auth.policy.Statement

      val msg1 = "allowAllStatement with iamUser1"
      val stmt1: Statement = IAM.allowAllStatement(bucket, List(iamUser1.principal), msg1)
      bucket.policy = List(stmt1)
      val json1 = bucket.policyAsJson
      assert(json1.contains(s""""Sid":"$msg1""""))
      assert(json1.contains(s""":user/iamUser1"""))
      assert(json1.contains(s""""Action":"s3:*""""))
      assert(json1.contains(s""""Resource":"arn:aws:s3:::$bucketName""""))

      val msg2 = "allowAllStatement with iamUser1 and iamuser2"
      val stmt2: Statement = IAM.allowAllStatement(bucket, List(iamUser1.principal, iamUser2.principal), msg2)
      bucket.policy = List(stmt2)
      val json2 = bucket.policyAsJson
      assert(json2.contains(s""""Sid":"$msg2""""))
      assert(json2.contains(s""":user/iamUser1"""))
      assert(json2.contains(s""":user/iamUser2"""))

      assert(iam.findUser(iamUser1Name).isDefined)
      iamUser1.deleteUser()
      assert(iam.findUser(iamUser1Name).isEmpty)
      iamUser2.deleteUser()
      ()
    }
  }
}
