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

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.sqs.AmazonSQSAsyncClient
import scala.collection.JavaConverters._

object SQS {
  def apply(implicit awsCredentials: AWSCredentials): SQS = new SQS()(awsCredentials)
}

class SQS()(implicit val awsCredentials: AWSCredentials) {
  implicit val sqs = this
  implicit val sqsClient: AmazonSQSAsyncClient = new AmazonSQSAsyncClient(awsCredentials)

}

trait  SQSImplicits {
}
