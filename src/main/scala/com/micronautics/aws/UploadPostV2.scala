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

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.s3.model.Bucket
import org.joda.time.{DateTime, DateTimeZone, Duration}
import scala.util.{Success, Failure, Try}

/** The implicit `AmazonIdentityManagementClient` instance determines the AIM user based on the AWS access key ID in the
  * implicit `AWSCredentials` instance in scope. */
object UploadPostV2 extends S3Implicits with CFImplicits {
  import java.net.URL
  import java.io.File

  val Logger = org.slf4j.LoggerFactory.getLogger("UploadPostV2")

  /** @param file File to upload
    * @param bucket Bucket to deliver the upload to; must have CORS set for POST and the bucket policy must grant the
    *               user referenced in the implicit `AmazonIdentityManagementClient` instance the privilege to upload
    * @param acl Access Control List for the uploaded item
    * @param expiryDuration specifies the maximum `Duration` the upload has to complete before AWS terminates it with an error */
  def apply(file: File, bucket: Bucket, key: String, acl: AclEnum, expiryDuration: Duration=Duration.standardHours(1))
           (implicit s3: S3, cf: CloudFront): Try[Boolean] = {
    // The key is not part of the upload URL; also note the short URL does not include the AWS region
    val uploadUrl = new URL(s"http://${bucket.getName}.s3.amazonaws.com")
    val parameters = params(file, bucket, key, acl, expiryDuration, s3.awsCredentials)
    uploadPost(file, uploadUrl, parameters).flatMap( _ =>
      invalidateDistributions(bucket, key)
    )
  }

  def invalidateDistributions(bucket: Bucket, key: String)(implicit s3: S3, cf: CloudFront): Try[Boolean] =
    Try { RichBucket(bucket).invalidate(key)>0 }

  protected[aws] def params(file: File, bucket: Bucket, key: String, acl: AclEnum, expiryDuration: Duration, awsCredentials: AWSCredentials): Map[String, String] = {
    val awsUpload = new UploadPostV2(bucket, expiryDuration)(awsCredentials)
    val contentLength = file.length
    val fileName = file.getName
    val saep = awsUpload.SignAndEncodePolicy(fileName, contentLength, acl)
    Map[String, String](
      "key"            -> key,
      "AWSAccessKeyId" -> awsCredentials.getAWSAccessKeyId,
      "acl"            -> acl.display,
      "policy"         -> saep.encodedPolicy,
      "signature"      -> saep.signedPolicy,
      "Content-Type"   -> saep.contentType
    )
  }

  /** There are two versions of upload via POST to S3; this method uses
    * [v2](http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingHTTPPOST.html), not
    * [v4](http://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-authentication-HTTPPOST.html) */
  protected[aws] def uploadPost(file: File, uploadUrl: URL, params: Map[String, String]): Try[Boolean] = {
    import org.apache.http.entity.ContentType
    import org.apache.http.entity.mime.{MultipartEntityBuilder, HttpMultipartMode}
    import org.apache.http.entity.mime.content.FileBody
    import org.apache.http.impl.client.HttpClientBuilder
    import org.apache.http.client.methods.{CloseableHttpResponse, HttpPost}
    import java.nio.charset.Charset

    Logger.info(s"uploadUrl=${uploadUrl.toString}")
    val httpPost = new HttpPost(uploadUrl.toString)
    val httpClient = HttpClientBuilder.create.build
    try {
      val builder = MultipartEntityBuilder.create
      builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
      params.foreach { case (key, value) =>
        Logger.info(s"Adding $key: $value")
        builder.addTextBody(key, value)
      }
      builder.setCharset(Charset.forName("UTF-8"))

      Logger.info(s"Adding file: ${file.getAbsolutePath}")
      val fileBody = new FileBody(file, ContentType.DEFAULT_BINARY)
      builder.addPart("file", fileBody)
      val entity = builder.build
      httpPost.setEntity(entity)

      val response: CloseableHttpResponse = httpClient.execute(httpPost)
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode>=300) {
        Logger.error(s"statusCode $statusCode}")
        Failure(new Exception(s"statusCode $statusCode}"))
      } else {
        Success(true)
      }
    } catch {
      case e: Exception =>
        Failure(e)
    }
  }
}

/** Heavy computation for preparing upload, mostly having to do with security.
  * The AIM user on whose behalf the upload is performed is determined from the AWS access key ID in the implicit `AWSCredentials` instance.
  * @param expiryDuration specifies the maximum `Duration` the upload has to complete before AWS terminates it with an error */
class UploadPostV2(bucket: Bucket, expiryDuration: Duration=Duration.standardHours(1))
                  (implicit awsCredentials: AWSCredentials) {
  import com.micronautics.aws.AclEnum._
  import sun.misc.BASE64Encoder

  /** @param key has path, without leading slash, including filetype */
  def policyText(key: String, contentLength: Long, acl: AclEnum = privateAcl): String = {
    import org.joda.time.format.ISODateTimeFormat
    val expiryDT = new DateTime(DateTimeZone.UTC).plus(expiryDuration)
    val expiryFormatted = ISODateTimeFormat.dateHourMinuteSecond().print(expiryDT)
    val lastSlash = key.lastIndexOf("/")
    val keyPrefix = if (lastSlash < 0) "" else key.substring(0, lastSlash)
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

  /** @param policyText must be encoded with UTF-8 */
  def signPolicy(policyText: String, contentLength: Long): String = {
    import javax.crypto.Mac
    import javax.crypto.spec.SecretKeySpec

    assert(awsCredentials.getAWSSecretKey.nonEmpty)
    val hmac = Mac.getInstance("HmacSHA1")
    hmac.init(new SecretKeySpec(awsCredentials.getAWSSecretKey.getBytes("UTF-8"), "HmacSHA1"))
    val encodedPolicy: String = policyEncoder(policyText, contentLength)
    val finalizedHmac = hmac.doFinal(encodedPolicy.getBytes("UTF-8"))
    val signature = new BASE64Encoder().encode(finalizedHmac)
    val result = signature.replaceAll("\n|\r", "")
    result
  }

  /** @param key has path, without leading slash, including filetype
    * @param acl must either be "public" or "public-read"
    * @return tuple containing encoded policy and signed policy for given key and contentLength */
  case class SignAndEncodePolicy(key: String, contentLength: Long, acl: AclEnum = AclEnum.privateAcl) {
    assert(!key.startsWith("/"))
    assert(acl == AclEnum.publicAcl || acl == AclEnum.privateAcl)
    Logger.debug(s"Signing '$key' with awsSecretKey='${awsCredentials.getAWSSecretKey}' and acl='$acl'")
    val policy = policyText(key, contentLength, acl)
    val encodedPolicy = policyEncoder(policy, contentLength)
    val signedPolicy = signPolicy(policy, contentLength)
    val contentType = guessContentType(key)
  }
}
