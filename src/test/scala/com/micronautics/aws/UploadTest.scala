package com.micronautics.aws

import java.io.File

import com.amazonaws.auth.policy.{Principal, Statement}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.s3.model.Bucket
import com.micronautics.aws.AclEnum._
import com.micronautics.aws.{Logger => _}
import java.nio.charset.Charset
import org.apache.commons.io.FileUtils
import org.apache.http.HttpResponse
import org.apache.http.client.methods.{CloseableHttpResponse, HttpPost}
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.util.EntityUtils
import scala.util.{Failure, Success, Try}
import org.scalatest._

class UploadTest extends WordSpec with TestBase with IAMImplicits with S3Implicits {
  import java.net.URL
  import UploadTest._
  import IAMTest._

  val Logger = org.slf4j.LoggerFactory.getLogger("UploadTest")
  implicit val iamClient = iam.iamClient

  def upload(file: File, uploadUrl: URL, params: Map[String, String]): Unit = {
    uploadViaHttpClient(uploadUrl, params, file) match {
      case Success(body) =>
      case Failure(throwable) =>
        fail(throwable.toString)
    }
    ()
  }

  "uploadAsset" should {
    "sign, encode and upload publicly readable file to AWS S3" in {
      // Only lazy vals and defs are allowed in this block; no vals or any other code blocks, otherwise delayedInit() will
      // get invoked twice and therefore around() will get invoked twice
      Logger.info(s"Creating bucket $bucketName")
      createBucket(bucketName)

      Logger.info("Uploading asset")
      val (uploadUrl1, sae1, params1) = computeSAE(bucket, privateAcl)
      sae1.encodedPolicy must not be equal("")
      sae1.signedPolicy  must not be equal("")
      sae1.contentType   mustEqual "image/png"
      upload(file, uploadUrl1, params1)  // 204 no content

      Logger.info("Uploading homework")
      val (uploadUrl2, sae2, params2) = computeSAE(bucket, publicAcl)
      sae1.encodedPolicy must not be equal("")
      sae1.signedPolicy  must not be equal("")
      //sae1.contentType   mustEqual "image/png"
      upload(file, uploadUrl2, params2)

      val url = bucket.resourceUrl(aKey).replace("https:", "http:") // step around SSL mismatch
      val stream = new URL(url).openStream
      val actual: Array[Byte] = try {
        org.apache.commons.io.IOUtils.toByteArray(stream)
      } finally {
        stream.close()
      }
      val desired: Array[Byte] = FileUtils.readFileToByteArray(file)
      assert(actual === desired)
    }
  }
}

object UploadTest extends S3Implicits {
  import java.net.URL
  import java.io.File

  val Logger = org.slf4j.LoggerFactory.getLogger("UploadTest")

  val fileName = "uploadMe.png"
  val resourcesDir = "src/test/resources"

  val contentLength = new File(fileName).length
  val aKey = s"uploadDirectory/$fileName"
  val file = new File(resourcesDir, fileName)

  def computeSAE(bucket: Bucket, acl: AclEnum)(implicit s3: S3): (URL, SignedAndEncoded, Map[String, String]) = {
    val uploadUrl = new URL(s"http://${bucket.getName}.s3.amazonaws.com") // key is NOT part of the url, also note the short URL (region is not included)
    val awsUpload = new AWSUpload(bucket)(s3.awsCredentials)
    val sae: SignedAndEncoded = awsUpload.signAndEncodePolicy(fileName, contentLength, acl, s3.awsCredentials.getAWSSecretKey)
    val params = Map[String, String](
      "key"            -> aKey,
      "AWSAccessKeyId" -> s3.awsCredentials.getAWSAccessKeyId,
      "acl"            -> acl.display,
      "policy"         -> sae.encodedPolicy,
      "signature"      -> sae.signedPolicy,
      "Content-Type"   -> sae.contentType
    )
    (uploadUrl, sae, params)
  }

  def responseContent(response: HttpResponse): String = {
    val resEntity = response.getEntity
    if (resEntity != null)
      Logger.info("  Response content length: " + resEntity.getContentLength)
    EntityUtils.consume(resEntity)
    val result = try {
      io.Source.fromInputStream(resEntity.getContent)(io.Codec.ISO8859).mkString
    } catch {
      case e: Exception => e.getMessage
    }
    result
  }

  /** Seems there are two versions of upload via POST to S3, I think I am using v2:
    * v2 http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingHTTPPOST.html
    * v4 http://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-authentication-HTTPPOST.html */
  def uploadViaHttpClient(uploadUrl: URL, params: Map[String, String], file: File): Try[Boolean] = {
    import org.apache.http.impl.client.HttpClientBuilder
    import org.apache.http.entity.mime.{HttpMultipartMode, MultipartEntityBuilder}

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
      AWSUpload.setBucketPolicy(bucket, allowStatements)
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
