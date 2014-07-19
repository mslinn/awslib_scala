package com.micronautics.aws

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfter, WordSpec, Matchers}

class SNSTest extends WordSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with Fixtures {
  def sns = new SNS(s3.awsCredentials.getAWSAccessKeyId, s3.awsCredentials.getAWSSecretKey)

  "Blah" must {
    "blah" in {
    }
  }
}
