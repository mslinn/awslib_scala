package com.micronautics.aws

import com.amazonaws.auth.{BasicAWSCredentials, PropertiesCredentials, AWSCredentials}
import com.amazonaws.services.sns.AmazonSNSClient
import java.lang.{Exception, String}
import java.io.InputStream
import scala.Exception
import com.amazonaws.services.s3.AmazonS3Client

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
}
