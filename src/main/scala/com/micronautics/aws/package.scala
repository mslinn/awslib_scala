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

import com.amazonaws.auth.BasicAWSCredentials
import scala.util.control.NoStackTrace

/** @param arnString Typical value: `arn:aws:sns:us-east-1:031372724784:transcoderComplete` */
case class Arn(arnString: String) {
  import com.amazonaws.services.sns.model.Topic

  println(s"arnString=$arnString")
  val Array(_, _, serviceName, region, amiOwnerId, name) = arnString.split(":")

  def asTopic: Topic = new Topic().withTopicArn(arnString)
}

case class Credentials(awsAccountName: String, accessKey: String, secretKey: String) extends BasicAWSCredentials(accessKey, secretKey) {
  val asBasicAWSCredentials: BasicAWSCredentials = new BasicAWSCredentials(accessKey, secretKey)
}

class ExceptTrace(msg: String) extends Exception(msg) with NoStackTrace

object ExceptTrace {
  def apply(msg: String): ExceptTrace = new ExceptTrace(msg)

  def apply(msg: String, exception: Exception): ExceptTrace = new ExceptTrace(msg)
}

case class Subscription(arn: Arn) {
  import com.amazonaws.services.sns.AmazonSNSClient

  /** @return Option[String] containing SubscriptionId */
  def confirm(token: String)(implicit snsClient: AmazonSNSClient): Option[Arn] = {
    import com.amazonaws.services.sns.model.ConfirmSubscriptionRequest

    val request = new ConfirmSubscriptionRequest().withTopicArn(arn.arnString).withToken(token)
    val maybeSubscriptionArn = Option(snsClient.confirmSubscription(request).getSubscriptionArn.asArn)
    Logger.trace(s"SNS Subscription confirmed with Arn ${maybeSubscriptionArn.get}")
    maybeSubscriptionArn
  }
}
