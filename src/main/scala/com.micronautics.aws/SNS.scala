package com.micronautics.aws

import com.amazonaws.auth.{BasicAWSCredentials, PropertiesCredentials, AWSCredentials}
import com.amazonaws.services.sns.AmazonSNSClient
import java.lang.{Exception, String}
import java.io.InputStream
import scala.Exception
import com.amazonaws.services.sns.model._
import collection.JavaConverters._

class SNS {
  var sns: AmazonSNSClient = _
  var exception: Exception = null

  var awsCredentials: AWSCredentials = new AWSCredentials {
    def getAWSAccessKeyId: String = {
      System.getenv("accessKey")
    }

    def getAWSSecretKey: String = {
      System.getenv("secretKey")
    }
  }

  try {
    sns = if (awsCredentials.getAWSAccessKeyId != null && awsCredentials.getAWSSecretKey != null) {
      new AmazonSNSClient(awsCredentials)
    } else {
      val inputStream: InputStream = getClass.getClassLoader.getResourceAsStream("AwsCredentials.properties")
      awsCredentials = new PropertiesCredentials(inputStream)
      new AmazonSNSClient(awsCredentials)
    }
  } catch {
    case ex: Exception => {
      exception = ex
    }
  }

  def this(key: String, secret: String) {
    this()
    awsCredentials = new BasicAWSCredentials(key, secret)
    sns = new AmazonSNSClient(awsCredentials)
  }

  /** Creates a topic if it does not exist. Topics should contain a string unique to the AWS account, such as the publishing server's domain name
   * @return Some(ARN of the Topic) or None if error */
  def findOrCreateTopic(name: String): Option[String] = {
    try {
      val ltr: ListTopicsResult = sns.listTopics(new ListTopicsRequest)
      val maybeTopic = ltr.getTopics.asScala.toList.find(_.getTopicArn.endsWith(":" + name))
      maybeTopic.map(_.getTopicArn).orElse {
        val ctr: CreateTopicResult = sns.createTopic(new CreateTopicRequest(name))
        Some(ctr.getTopicArn)
      }
    } catch {
      case e: Exception =>
        // TODO set up logging
        //Logger.warn(e)
        println(e.toString)
        None
    }
  }

  /** @param protocol is most likely "http" or "https"
    * @param endpoint is URL that will receive HTTP POST of ConfirmSubscription action */
  def subscribe(arn: String, protocol: String, endpoint: String) = {
    sns.subscribe(new SubscribeRequest(arn, protocol, endpoint))
  }
}
