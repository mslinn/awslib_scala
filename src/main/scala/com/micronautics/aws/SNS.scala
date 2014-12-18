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
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model._

import scala.collection.JavaConverters._

object SNS {
  def apply(implicit awsCredentials: AWSCredentials): SNS = new SNS()(awsCredentials)
}

class SNS()(implicit val awsCredentials: AWSCredentials) {
  implicit val sns = this
  implicit val snsClient: AmazonSNSClient = new AmazonSNSClient(awsCredentials)

  /** Creates a topic if it does not exist. Topics should contain a string unique to the AWS account, such as the publishing server's domain name
   * @return Some(ARN of the Topic) or None if error */
  def findOrCreateTopic(name: String): Option[String] = {
    try {
      val ltr: ListTopicsResult = snsClient.listTopics(new ListTopicsRequest)
      val maybeTopic = ltr.getTopics.asScala.toList.find(_.getTopicArn.endsWith(":" + name))
      maybeTopic.map(_.getTopicArn).orElse {
        val ctr: CreateTopicResult = snsClient.createTopic(new CreateTopicRequest(name))
        Some(ctr.getTopicArn)
      }
    } catch {
      case e: Exception =>
        Logger.warn(e.getMessage)
        println(e.toString)
        None
    }
  }

  /** @param protocol is most likely "http" or "https"
    * @param endpoint is URL that will receive HTTP POST of ConfirmSubscription action */
  def subscribe(arn: String, protocol: String, endpoint: String) = {
    snsClient.subscribe(new SubscribeRequest(arn, protocol, endpoint))
  }
}

trait SNSImplicits {

}
