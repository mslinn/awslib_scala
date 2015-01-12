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
import org.scalatest.concurrent.AsyncAssertions
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, MustMatchers, Suite}
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}

trait TestBase extends MustMatchers with BeforeAndAfter with BeforeAndAfterAll with SNSImplicits { this: BeforeAndAfterAll with Suite =>
//  lazy implicit val awsCredentials: AWSCredentials = new BasicAWSCredentials("blahblah", "blahblah")
  lazy implicit val awsCredentials: AWSCredentials = maybeCredentialsFromEnv("TEST_").getOrElse(
                                                       maybeCredentialsFromFile.getOrElse(
                                                         sys.error("No AWS credentials found in environment variables and no .s3 file was found in the working directory, or a parent directory.")))
  lazy implicit val s3: S3 = new S3()
  lazy implicit val cf: CloudFront = new CloudFront()
  lazy implicit val et: ElasticTranscoder = new ElasticTranscoder()
  lazy implicit val iam: IAM = new IAM()
  lazy implicit val sns: SNS = new SNS()
}

abstract class PlaySpecServer extends PlaySpec
                              with OneServerPerSuite
                              with AsyncAssertions
                              with play.api.mvc.Results
                              with SNSImplicits {
//  lazy implicit val awsCredentials: AWSCredentials = new BasicAWSCredentials("blahblah", "blahblah")
  lazy implicit val awsCredentials: AWSCredentials = maybeCredentialsFromEnv("TEST_").getOrElse(
                                                       maybeCredentialsFromFile.getOrElse(
                                                         sys.error("No AWS credentials found in environment variables and no .s3 file was found in the working directory, or a parent directory.")))
  lazy implicit val s3: S3 = new S3()
  lazy implicit val cf: CloudFront = new CloudFront()
  lazy implicit val et: ElasticTranscoder = new ElasticTranscoder()
  lazy implicit val iam: IAM = new IAM()
  lazy implicit val sns: SNS = new SNS()
}
