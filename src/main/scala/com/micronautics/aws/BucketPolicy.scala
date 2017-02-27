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

import com.amazonaws.auth.policy.{Principal, Statement}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.s3.model.Bucket
import scala.util.Try

/** Convenience methods which created a bucket with the given bucketName, enables web site and CORS, and sets the bucket
  * policy so the owner can upload. You may want to write similar code that generates a policy that follows AWS best
  * practices instead. */
object BucketPolicy {
  /** This method is idempotent
    * Side effect: sets policy for given AWS S3 upload bucket */
  def setPolicy(bucket: Bucket, statements: List[Statement])(implicit s3: S3): Bucket = {
    try {
      bucket.policy = statements
      Logger.debug(s"${bucket.getName} bucket policy=${bucket.policy}")
    } catch {
      case ignored: Exception =>
        Logger.debug(s"setPolicy: ${ignored.toString}")
    }
    bucket.enableCors()
    bucket
  }

  /** The implicit `AmazonIdentityManagementClient `instance determines the AIM user based on the AWS access key ID in the
    * implicit `AWSCredentials` instance in scope.
    * @return typical value: Success("arn:aws:iam::031372724784:root") */
  def arnUser(implicit iamClient: AmazonIdentityManagement): Try[String] =
    Try { iamClient.getUser.getUser.getArn }

  /** @return Try[Principal] for `arnUser`. typical principalUser.getId value: arn:aws:iam::031372724784:root */
  def principalUser(implicit iamClient: AmazonIdentityManagement): Try[Principal] =
    arnUser.map(arn => new Principal(arn))

  def allowUserEverythingStatement(bucket: Bucket)(implicit iamClient: AmazonIdentityManagement): Statement = {
    val principals: Seq[Principal] = principalUser.toOption.toList
    bucket.allowAllStatement(principals, "Allow user to do everything")
  }

  def createBucket(bucketName: String)(implicit s3: S3, iamClient: AmazonIdentityManagement): Bucket = {
    try {
      Logger.info(s"Setting up bucket $bucketName")
      val bucket = s3.createBucket(bucketName)
      bucket.enableWebsite()
      bucket.enableCors()
      val allowStatements = List(allowUserEverythingStatement(bucket))
      setPolicy(bucket, allowStatements)
      bucket
    } catch {
      case e: Exception =>
        Logger.info(s"setupBucket: ${e.toString}")
        try {
          //bucket.delete()
        } catch {
          case ignored: Exception =>
            //Logger.debug(s"Ignoring: $ignored")
        }
        throw new Exception(s"Exception setting up $bucketName; $e")
    }
  }
}
