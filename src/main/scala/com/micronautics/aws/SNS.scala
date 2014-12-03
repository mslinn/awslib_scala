package com.micronautics.aws

import AwsCredentials._
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model._

import scala.collection.JavaConverters._

object SNS {
  def apply(implicit awsCredentials: AWSCredentials, iamClient: AmazonSNSClient=new AmazonSNSClient): SNS =
    new SNS()(awsCredentials, iamClient)
}

class SNS()(implicit val awsCredentials: AWSCredentials, val snsClient: AmazonSNSClient=new AmazonSNSClient) {
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
