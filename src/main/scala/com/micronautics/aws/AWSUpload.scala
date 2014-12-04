package com.micronautics.aws

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.s3.model.Bucket
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone, Duration}
import org.slf4j.LoggerFactory
import sun.misc.BASE64Encoder

case class SignedAndEncoded(
  encodedPolicy: String,
  signedPolicy: String,
  contentType: String
)

object AWSUpload {
  val Logger = LoggerFactory.getLogger("AWSUpload")
  val publicAcl = "public-read"
  val privateAcl = "private"
}

/** @param expiryDuration times out policy in one hour by default */
class AWSUpload(val bucket: Bucket, val expiryDuration: Duration=Duration.standardHours(1))(implicit awsCredentials: AWSCredentials) {
  import com.micronautics.aws.AWSUpload._

  /** @param key has path, without leading slash, including filetype */
  def policyText(key: String, contentLength: Long, acl: String="private"): String = {
    val expiryDT = new DateTime(DateTimeZone.UTC).plus(expiryDuration)
    val expiryFormatted = ISODateTimeFormat.dateHourMinuteSecond().print(expiryDT)
    val lastSlash = key.lastIndexOf("/")
    val keyPrefix = if (lastSlash<0) "" else key.substring(0, lastSlash)
    val policy = s"""{
                    |  "expiration": "${expiryFormatted}Z",
                    |  "conditions": [
                    |    {"bucket": "${bucket.getName}"},
                    |    ["starts-with", "$$key", "$keyPrefix"],
                    |    {"acl": "$acl"},
                    |    ["starts-with", "$$Content-Type", ""],
                    |  ]
                    |}""".stripMargin
    Logger.debug(s"policyText for $key is $policy")
    policy
  }

  def policyEncoder(policyText: String, contentLength: Long): String = {
    val encodedPolicy = new BASE64Encoder().encode(policyText.getBytes("UTF-8"))
    encodedPolicy.replaceAll("\n|\r", "")
  }

  /** @param policyText must be encoded with UTF-8
    * @param awsSecretKey must be encoded with UTF-8 */
  def signPolicy(policyText: String, contentLength: Long, awsSecretKey: String): String = {
    assert(awsSecretKey.nonEmpty)
    val hmac = Mac.getInstance("HmacSHA1")
    hmac.init(new SecretKeySpec(awsSecretKey.getBytes("UTF-8"), "HmacSHA1"))
    val encodedPolicy: String = policyEncoder(policyText, contentLength)
    val finalizedHmac = hmac.doFinal(encodedPolicy.getBytes("UTF-8"))
    val signature = new BASE64Encoder().encode(finalizedHmac)
    val result = signature.replaceAll("\n|\r", "")
    result
  }

  /** @param key has path, without leading slash, including filetype
    * @param acl must either be "public" or "public-read"
    * @return tuple containing encoded policy and signed policy for given key and contentLength */
  def signAndEncodePolicy(key: String, contentLength: Long, acl: String = privateAcl,
                          awsSecretKey: String = awsCredentials.getAWSSecretKey): SignedAndEncoded = {
    assert(!key.startsWith("/"))
    assert(acl==publicAcl || acl==privateAcl)
    Logger.debug(s"Signing '$key' with awsSecretKey='$awsSecretKey' and acl='$acl'")
    val policy = policyText(key, contentLength, acl)
    val encodedPolicy = policyEncoder(policy, contentLength)
    val signedPolicy = signPolicy(policy, contentLength, awsSecretKey)
    SignedAndEncoded(encodedPolicy=encodedPolicy, signedPolicy=signedPolicy, contentType=guessContentType(key))
  }
}
