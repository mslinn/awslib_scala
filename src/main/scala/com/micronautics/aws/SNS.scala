/* Copyright 2012-2016 Micronautics Research Corporation.
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
import java.net.URL
import scala.collection.JavaConverters._

object SNS {
  def apply(implicit awsCredentials: AWSCredentials): SNS = new SNS()(awsCredentials)
}

class SNS()(implicit val awsCredentials: AWSCredentials) {
  implicit val sns: SNS = this
  implicit val snsClient: AmazonSNSClient = new AmazonSNSClient(awsCredentials)

  /** @return Option[String] containing SubscriptionId */
  def confirmSubscription(token: String, arn: String): Option[String] = {
    val subscriptionId = Option(snsClient.confirmSubscription(new ConfirmSubscriptionRequest().withTopicArn(arn).withToken(token)).getSubscriptionArn)
    Logger.trace(s"SNS Confirmation $subscriptionId")
    subscriptionId
  }

  def deleteTopic(topic: Topic): Unit = topic.delete()

  /** Creates a topic.
    * Topics should contain a string unique to the AWS account, such as the publishing server's domain name
    * @return desired topic */
  def createTopic(name: String): Option[Topic] =
    try {
      val topic = Some(snsClient.createTopic(new CreateTopicRequest(name)).getTopicArn.asTopic)
      Logger.debug(s"Created SNS topic $topic")
      topic
    } catch {
      case e: Exception =>
        Logger.warn(e.getMessage)
        None
    }

  /** Creates a topic if it does not exist.
    * Topics should contain a string unique to the AWS account, such as the publishing server's domain name
    * @return newly created topic */
  def findOrCreateTopic(name: String): Option[Topic] = findTopic(name) orElse { createTopic(name) }

  /** @return the topic with the given name if it exists. */
  def findTopic(name: String): Option[Topic] =
    try {
      val topic = snsClient.listTopics(new ListTopicsRequest).getTopics.asScala.find(_.name==name)
      Logger.debug(s"Found SNS topic $topic")
      topic
    } catch {
      case e: Exception =>
        Logger.warn(e.getMessage)
        None
    }

  def publish(topic: Topic, message: String): Arn = topic.publish(message)

  def subscribe(topic: Topic, url: URL): Unit = topic.subscribe(url)
}

trait  SNSImplicits {
  implicit class RichString(string: String) {
    def asArn: Arn = Arn(string)

    def asTopic: Topic = new Topic().withTopicArn(string)

    def asUrl: URL = new URL(string)
  }

  implicit class RichTopic(topic: Topic) {
    def arn: Arn = topic.getTopicArn.asArn

    def delete()(implicit snsClient: AmazonSNSClient): Unit =
      try {
       snsClient.deleteTopic(topic.getTopicArn)
       Logger.debug(s"SNS topic ${topic.getTopicArn} was deleted")
      } catch {
        case e: Exception =>
          Logger.warn(e.getMessage)
      }

    def name: String = topic.arn.name

    /** @return published message id */
    def publish(message: String)(implicit snsClient: AmazonSNSClient): Arn = {
      val arn = snsClient.publish(new PublishRequest().withTopicArn(topic.getTopicArn).withMessage(message)).getMessageId.asArn
      Logger.debug(s"Published SNS message $message")
      arn
    }

    /** @param url will receive HTTP POST of ConfirmSubscription action
      * @return ARN of subscription */
    def subscribe(url: URL)(implicit snsClient: AmazonSNSClient): Unit = {
      snsClient.subscribe(new SubscribeRequest(topic.getTopicArn, url.getProtocol, url.toString))
      Logger.debug(s"Subscribed to SNS endpoint $url")
    }
  }
}
