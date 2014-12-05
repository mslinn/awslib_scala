/* Copyright 2012-2014 Micronautics Research Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License. */

package com.micronautics

import java.io.{File, InputStream}
import java.net.URL

import com.amazonaws.AmazonClientException
import com.amazonaws.auth.{BasicAWSCredentials, AWSCredentials}
import com.amazonaws.auth.policy.actions.S3Actions
import com.amazonaws.auth.policy.{Principal, Statement}
import com.amazonaws.services.identitymanagement.model.{CreateAccessKeyRequest}
import com.amazonaws.services.s3.model.{Bucket, BucketPolicy, PutObjectResult, S3ObjectSummary}
import org.joda.time.Duration
import com.amazonaws.services.identitymanagement.model.{User => IAMUser}

/**
 *
 */
package object aws {
  private val contentTypeMap = Map(
    "css"   ->"text/css",
    "doc"   -> "application/vnd.ms-word",
    "dot"   -> "application/vnd.ms-word",
    "docx"  -> "application/vnd.ms-word",
    "dtd"   -> "application/xml-dtd",
    "flv"   -> "video/x-flv",
    "gif"   -> "image/gif",
    "gzip"  -> "application/gzip",
    "gz"    -> "application/gzip",
    "html"  -> "text/html",
    "htm"   -> "text/html",
    "shtml" -> "text/html",
    "jsp"   -> "text/html",
    "php"   -> "text/html",
    "ico"   -> "image/vnd.microsoft.icon",
    "jpg"   -> "image/jpeg",
    "js"    -> "application/javascript",
    "json"  -> "application/json",
    "mkv"   -> "video/x-matroska",
    "mov"   -> "video/quicktime",
    "mp3"   -> "audio/mpeg",
    "mpeg"  -> "audio/mpeg",
    "mpg"   -> "video/mpeg",
    "mp4"   -> "video/mp4",
    "ogg"   -> "video/ogg",
    "pdf"   -> "application/pdf",
    "png"   -> "image/png",
    "ppt"   -> "application/vnd.ms-powerpoint",
    "pptx"  -> "application/vnd.ms-powerpoint",
    "ps"    -> "application/postscript",
    "qt"    -> "video/quicktime",
    "ra"    -> "audio/vnd.rn-realaudio",
    "svg"   -> "image/svg+xml",
    "tif"   -> "image/tiff",
    "tiff"  -> "image/tiff",
    "txt"   -> "text/plain",
    "xls"   -> "application/vnd.ms-excel",
    "xlsx"  -> "application/vnd.ms-excel",
    "xml"   -> "application/xml",
    "vcard" -> "text/vcard",
    "wav"   -> "audio/vnd.wave",
    "webm"  -> "video/webm",
    "wmv"   -> "video/x-ms-wmv",
    "zip"   -> "application/zip"
  ).withDefaultValue("application/octet-stream")

  def guessContentType(key: String): String = {
    val keyLC: String = key.substring(math.max(0, key.lastIndexOf('.')+1)).trim.toLowerCase
    contentTypeMap(keyLC)
  }

  implicit class RichBucket(val bucket: Bucket)(implicit val s3: S3) {
    def allObjectData(prefix: String): List[S3ObjectSummary] = s3.allObjectData(bucket.getName, prefix)

    def exists: Boolean = s3.bucketExists(bucket.getName)

    def location: String = s3.bucketLocation(bucket.getName)

    def create(): Bucket = s3.createBucket(bucket.getName)

    @throws(classOf[AmazonClientException])
    def delete(): Unit = s3.deleteBucket(bucket.getName)

    def deleteObject(key: String): Unit = s3.deleteObject(bucket.getName, key)

    def deletePrefix(prefix: String): Unit = s3.deletePrefix(bucket.getName, prefix)

    def disableWebsite(): Unit = s3.disableWebsite(bucket.getName)

    def downloadAsStream(key: String): InputStream = s3.downloadFile(bucket.getName, key)

    def downloadAsString(key: String): String = io.Source.fromInputStream(downloadAsStream(key)).mkString

    @throws(classOf[AmazonClientException])
    def empty(): Unit = s3.emptyBucket(bucket.getName)

    def enableCors(): Unit = s3.enableCors(bucket)

    def enableWebsite(): Unit = s3.enableWebsite(bucket.getName)

    def enableWebsite(errorPage: String): Unit = s3.enableWebsite(bucket.getName, errorPage)

    def isWebsiteEnabled: Boolean = s3.isWebsiteEnabled(bucket.getName)

    def listObjectsByPrefix(prefix: String): List[String] = s3.listObjectsByPrefix(bucket.getName, prefix)

    def listObjectsByPrefix(prefix: String, showSize: Boolean): List[String] = s3.listObjectsByPrefix(bucket.getName, prefix, showSize)

    def move(oldKey: String, newKey: String): Unit = s3.move(bucket.getName, oldKey, newKey)

    def move(oldKey: String, newBucketName: String, newKey: String): Unit = s3.move(bucket.getName, oldKey, newBucketName, newKey)

    def name: String = bucket.getName

    def policy_=(policyJson: String) = s3.setBucketPolicy(bucket, policyJson)

    def policy_=(statements: List[Statement]) = s3.setBucketPolicy(bucket, statements)

    def policy: BucketPolicy = s3.s3Client.getBucketPolicy(bucket.getName)

    def policyAsJson: String = policy.getPolicyText

    def policyEncoder(policyText: String, contentLength: Long, expiryDuration: Duration=Duration.standardHours(1)): String =
      new AWSUpload(bucket, expiryDuration)(s3.awsCredentials).policyEncoder(policyText, contentLength)

    def createPolicyText(key: String, contentLength: Long, acl: String="private", expiryDuration: Duration=Duration.standardHours(1)): String =
      new AWSUpload(bucket, expiryDuration)(s3.awsCredentials).policyText(key, contentLength, acl)

    def oneObjectData(prefix: String): Option[S3ObjectSummary] = s3.oneObjectData(bucket.getName, prefix)

    def resourceUrl(key: String): String = s3.resourceUrl(bucket.getName, key)

    def signAndEncodePolicy(key: String, contentLength: Long, acl: String=AWSUpload.privateAcl, awsSecretKey: String = s3.awsCredentials.getAWSSecretKey,
                            expiryDuration: Duration=Duration.standardHours(1)): SignedAndEncoded =
      new AWSUpload(bucket, expiryDuration)(s3.awsCredentials).signAndEncodePolicy(key, contentLength, acl, awsSecretKey)

    def signPolicy(policyText: String, contentLength: Long, awsSecretKey: String, expiryDuration: Duration=Duration.standardHours(1)): String =
      new AWSUpload(bucket, expiryDuration)(s3.awsCredentials).signPolicy(policyText, contentLength, awsSecretKey)

    def signUrl(url: URL, minutesValid: Int=60): URL = s3.signUrl(bucket, url, minutesValid)

    def signUrlStr(key: String, minutesValid: Int=0): URL = s3.signUrlStr(bucket, key, minutesValid)

    def uploadFile(key: String, file: File): PutObjectResult = s3.uploadFile(bucket.getName, key, file)

    def uploadFileOrDirectory(dest: String, file: File): Unit = s3.uploadFileOrDirectory(bucket.getName, dest, file)

    def uploadString(key: String, contents: String): PutObjectResult =  s3.uploadString(bucket.getName, key, contents)

    def uploadStream(key: String, stream: InputStream, length: Int): Unit = s3.uploadStream(bucket.getName, key, stream, length)
  }

  implicit class RichBucketIAM(val bucket: Bucket) {
    def allowAllStatement(principals: Seq[Principal], idString: String): Statement =
      IAM.allowAllStatement(bucket, principals, idString)

    def allowSomeStatement(principals: Seq[Principal], actions: Seq[S3Actions], idString: String): Statement =
      IAM.allowSomeStatement(bucket, principals, actions, idString)
  }

  implicit class RichIAMUser(val iamUser: IAMUser)(implicit iam: IAM) {
    def createCredentials: AWSCredentials = {
      val createAccessKeyRequest = new CreateAccessKeyRequest().withUserName(iamUser.getUserName)
      val accessKeyResult = iam.iamClient.createAccessKey(createAccessKeyRequest)
      new BasicAWSCredentials(accessKeyResult.getAccessKey.getAccessKeyId, accessKeyResult.getAccessKey.getSecretAccessKey)
    }

    def deleteAccessKeys(): Unit = iam.deleteAccessKeys(iamUser.getUserId)

    def deleteGroups(): Unit = iam.deleteGroups(iamUser.getUserId)

    def deleteLoginProfile(): Unit = iam.deleteLoginProfile(iamUser.getUserId)

    def deleteUser(): Unit = iam.deleteIAMUser(iamUser.getUserId)
  }
}
