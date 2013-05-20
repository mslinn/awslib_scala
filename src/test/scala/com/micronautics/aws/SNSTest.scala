package com.micronautics.aws

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfter, WordSpec}
import org.scalatest.matchers.MustMatchers

/**These tests will fail unless a file called AwsCredentials.properties is created in src/test/resources. */
class SNSTest extends WordSpec with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {
  val s3File1: S3File = Util.readS3File
  val creds = Util.getAuthentication(s3File1.accountName)

  val sns: SNS = creds match {
    case Some(credentials) =>
      S3Model.credentials = credentials
      new SNS(credentials.accessKey, credentials.secretKey)

    case None =>
      fail("Cannot locate .com.micronautics.aws file")
  }

  override def afterAll() {
  }

  override def beforeAll() {
  }

  "Blah" must {
    "blah" in {
    }
  }
}
