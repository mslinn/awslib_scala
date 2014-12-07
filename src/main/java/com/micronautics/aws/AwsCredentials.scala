package com.micronautics.aws

import java.io.InputStream

import com.amazonaws.auth.{PropertiesCredentials, BasicAWSCredentials, AWSCredentials}
import com.amazonaws.services.s3.AmazonS3Client
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object AwsCredentials {
  def apply: Try[(AWSCredentials, AmazonS3Client)] = {
    try {
      val awsCredentials = new BasicAWSCredentials(System.getenv("accessKey"), System.getenv("secretKey"))
      if (awsCredentials.getAWSAccessKeyId != null && awsCredentials.getAWSSecretKey != null) {
        Success((awsCredentials, new AmazonS3Client(awsCredentials)))
      } else {
        val inputStream: InputStream = getClass.getClassLoader.getResourceAsStream("AwsCredentials.properties")
        val propertiesCredentials = new PropertiesCredentials(inputStream)
        Success((propertiesCredentials, new AmazonS3Client(propertiesCredentials)))
      }
    } catch {
      case ex: Exception =>
        Logger.error(s"No AWS credential found in environment variables accessKey and secretKey or in AwsCredentials.properties")
        Failure(ex)
    }
  }
}
