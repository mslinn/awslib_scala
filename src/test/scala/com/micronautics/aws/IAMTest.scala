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

import com.amazonaws.services.s3.model.Bucket
import org.scalatest.WordSpec

class IAMTest extends WordSpec with TestBase with IAMImplicits {
  import IAMTest._
  import scala.util.Success

  val iamUser1Name = "testIamUser1"
  if (iam.findUser(iamUser1Name).isSuccess) // in case it is left over from a previous test
     iam.deleteUser(iamUser1Name)
  val Success((iamUser1, iamUser1Keys)) = iam.createIAMUser(iamUser1Name)

  val iamUser2Name = "testIamUser2"
  if (iam.findUser(iamUser2Name).isSuccess) // in case it is left over from a previous test
    iam.deleteUser(iamUser2Name)
  val Success((iamUser2, _)) = iam.createIAMUser(iamUser2Name) // keys are thrown away in tests so don't get confused with these

  override def afterAll(): Unit = {
    if (iam.findUser(iamUser1Name).isSuccess)
      iamUser1.deleteUser()
    if (iam.findUser(iamUser2Name).isSuccess)
        iam.deleteUser(iamUser2Name)
    super.afterAll()
  }

  "IAMUsers" must {
    "handle stuff" in {
      iamUser1.createAccessKeys() // IAMUsers can have 2 sets of keys
      assert(iamUser1.createAccessKeys().isFailure) // 3rd set of keys should go boom

      iamUser2.deleteAccessKeys()
      assert(iamUser2.createAccessKeys().isSuccess)

      iamUser2.deleteUser()
      assert(iamUser2.createAccessKeys().isFailure)
    }

    "be manipulable" ignore {
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

      assert(iam.findUser(iamUser1Name).isSuccess)
      assert(iamUser1.deleteUser().isSuccess)
      assert(iam.findUser(iamUser1Name).isSuccess)
      assert(iamUser2.deleteUser().isSuccess)
      ()
    }
  }
}

object IAMTest {
  val bucketName = s"www.test${ new java.util.Date().getTime }.com"

  def bucket(implicit s3: S3): Bucket = try {
      println(s"Creating bucket $bucketName")
      s3.createBucket(bucketName)
    } catch {
      case e: Exception =>
        throw new Error(s"Error creating bucket with accessKey=${ awsCredentials.getAWSAccessKeyId } and secretKey=${ awsCredentials.getAWSSecretKey }\n${ e.getMessage }")
    }
}
