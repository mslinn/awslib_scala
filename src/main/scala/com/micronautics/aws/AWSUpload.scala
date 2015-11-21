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

import java.nio.charset.Charset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.policy.{Principal, Statement}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.s3.model.Bucket
import org.apache.http.client.methods.{CloseableHttpResponse, HttpPost}
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.{HttpMultipartMode, MultipartEntityBuilder}
import org.apache.http.entity.mime.{MultipartEntityBuilder, HttpMultipartMode}
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone, Duration}
import sun.misc.BASE64Encoder

import scala.util.{Success, Failure, Try}

case class SignedAndEncoded(
  encodedPolicy: String,
  signedPolicy: String,
  contentType: String
)

object UploadPostV2 extends S3Implicits {
  import java.net.URL
  import java.io.File

  val Logger = org.slf4j.LoggerFactory.getLogger("UploadTest")

  def uploadPostV2(file: File, bucket: Bucket, aKey: String, acl: AclEnum)(implicit s3: S3): Try[Boolean] = {
    val uploadUrl = new URL(s"http://${bucket.getName}.s3.amazonaws.com") // key is NOT part of the url, also note the short URL (region is not included)
    val awsUpload = new UploadPostV2(bucket)(s3.awsCredentials)
    val contentLength = file.length
    val fileName = file.getName
    val sae: SignedAndEncoded = awsUpload.signAndEncodePolicy(fileName, contentLength, acl, s3.awsCredentials.getAWSSecretKey)
    val params = Map[String, String](
      "key"            -> aKey,
      "AWSAccessKeyId" -> s3.awsCredentials.getAWSAccessKeyId,
      "acl"            -> acl.display,
      "policy"         -> sae.encodedPolicy,
      "signature"      -> sae.signedPolicy,
      "Content-Type"   -> sae.contentType
    )
    uploadPost(file, uploadUrl, params)
  }

  /** There are two versions of upload via POST to S3; this method uses
    * [v2](http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingHTTPPOST.html), not
    * [v4](http://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-authentication-HTTPPOST.html) */
  def uploadPost(file: File, uploadUrl: URL, params: Map[String, String]): Try[Boolean] = {
    Logger.info(s"uploadUrl=${uploadUrl.toString}")
    val httpPost = new HttpPost(uploadUrl.toString)
    val httpClient = HttpClientBuilder.create.build
    try {
      val builder = MultipartEntityBuilder.create
      builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
      params.foreach { case (key, value) =>
        Logger.info(s"  Adding $key: $value")
        builder.addTextBody(key, value)
      }
      builder.setCharset(Charset.forName("UTF-8"))

      Logger.info(s"  Adding file: ${file.getAbsolutePath}")
      val fileBody = new FileBody(file, ContentType.DEFAULT_BINARY)
      builder.addPart("file", fileBody)
      val entity = builder.build
      httpPost.setEntity(entity)

      val response: CloseableHttpResponse = httpClient.execute(httpPost)
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode>=300) {
        Logger.error(s"  statusCode $statusCode}")
        Failure(new Exception(s"  statusCode $statusCode}"))
      } else {
        Success(true)
      }
    } catch {
      case e: Exception =>
        Failure(e)
    }
  }

  /** This method is idempotent
    * Side effect: sets policy for AWS S3 upload bucket */
  def setBucketPolicy(bucket: Bucket, statements: List[Statement])(implicit s3: S3): Bucket = {
    try {
      bucket.policy = statements
      Logger.debug(s"${bucket.getName} bucket policy=${bucket.policy}")
    } catch {
      case ignored: Exception =>
        Logger.debug(s"setBucketPolicy: ${ignored.toString}")
    }
    bucket.enableCors()
    bucket
  }

  /** Typical: arn:aws:iam::031372724784:root */
  def arnOwner(implicit iamClient: AmazonIdentityManagementClient): Try[String] =
    Try { iamClient.getUser.getUser.getArn }

  /** Typical: principalOwner.getId = arn:aws:iam::031372724784:root */
  def principalOwner(implicit iamClient: AmazonIdentityManagementClient): Try[Principal] =
    arnOwner.map(arn => new Principal(arn))

  def allowOwnerEverythingStatement(bucket: Bucket)(implicit iamClient: AmazonIdentityManagementClient): Statement = {
    val principals: Seq[Principal] = principalOwner.toOption.toList
    bucket.allowAllStatement(principals, "Allow root to do everything")
  }

  def createBucket(bucketName: String)(implicit s3: S3, iamClient: AmazonIdentityManagementClient): Bucket = {
    try {
      Logger.info(s"Setting up bucket $bucketName")
      val bucket = s3.createBucket(bucketName)
      bucket.enableWebsite()
      bucket.enableCors()
      val allowStatements = List(allowOwnerEverythingStatement(bucket))
      setBucketPolicy(bucket, allowStatements)
      bucket
    } catch {
      case e: Exception =>
        Logger.info(s"setupBucket: ${e.toString}")
        try {
          //bucket.delete()
        } catch {
          case ignored: Exception =>
            //Logger.debug(s"Ignoring: $ignored")
        }
        throw new Exception(s"Exception setting up $bucketName; $e")
    }
  }
}

/** @param expiryDuration times out policy in one hour by default */
class UploadPostV2(val bucket: Bucket, val expiryDuration: Duration=Duration.standardHours(1))(implicit awsCredentials: AWSCredentials) {
  import com.micronautics.aws.AclEnum._

  /** @param key has path, without leading slash, including filetype */
  def policyText(key: String, contentLength: Long, acl: AclEnum=privateAcl): String = {
    val expiryDT = new DateTime(DateTimeZone.UTC).plus(expiryDuration)
    val expiryFormatted = ISODateTimeFormat.dateHourMinuteSecond().print(expiryDT)
    val lastSlash = key.lastIndexOf("/")
    val keyPrefix = if (lastSlash<0) "" else key.substring(0, lastSlash)
    val policy = s"""{
                    |  "expiration": "${expiryFormatted}Z",
                    |  "conditions": [
                    |    {"bucket": "${bucket.getName}"},
                    |    ["starts-with", "$$key", "$keyPrefix"],
                    |    {"acl": "${acl.display}"},
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
  def signAndEncodePolicy(key: String, contentLength: Long, acl: AclEnum = privateAcl,
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
