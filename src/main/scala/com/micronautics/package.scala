/* Copyright 2012-2014 Micronautics Research Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License. */

package com.micronautics

/** When web site access is enabled, AWS content is accessed by paths constructed by concatenating the URL, a slash (/),
 and the keyed data.
 The keys must therefore consist of relative paths (relative directory name followed by a file name), and must not start with a leading slash.
 This program stores each file name (referred to by AWS as a key) without a leading slash.
 For example, assuming that the default file name is `index.html`, `http://domain.com` and `http://domain.com/` are translated to `http://domain.com/index.html`.

 As another example, the key for a file in a directory called `{WEBROOT}/blah/ick/yuck.html` is defined as `blah/ick/yuck.html`.

 For each directory, AWS creates a file of the same name, with the suffix `_$folder$`.
 If one of those files are deleted, the associated directory becomes unreachable. Don't mess with them.
 These hidden files are ignored by this program; users never see them because they are for AWS S3 internal use only. */
package object aws extends CFImplicits with IAMImplicits with S3Implicits with SNSImplicits {
  import com.amazonaws.auth.AWSCredentials
  import com.amazonaws.util.json.JSONObject
  import org.slf4j.LoggerFactory
  import play.api.libs.json.Json

  private[aws] lazy val Logger = LoggerFactory.getLogger("AWS")

  private val contentTypeMap = Map(
    "css" -> "text/css",
    "doc" -> "application/vnd.ms-word",
    "dot" -> "application/vnd.ms-word",
    "docx" -> "application/vnd.ms-word",
    "dtd" -> "application/xml-dtd",
    "flv" -> "video/x-flv",
    "gif" -> "image/gif",
    "gzip" -> "application/gzip",
    "gz" -> "application/gzip",
    "html" -> "text/html",
    "htm" -> "text/html",
    "shtml" -> "text/html",
    "jsp" -> "text/html",
    "php" -> "text/html",
    "ico" -> "image/vnd.microsoft.icon",
    "jpg" -> "image/jpeg",
    "js" -> "application/javascript",
    "json" -> "application/json",
    "mkv" -> "video/x-matroska",
    "mov" -> "video/quicktime",
    "mp3" -> "audio/mpeg",
    "mpeg" -> "audio/mpeg",
    "mpg" -> "video/mpeg",
    "mp4" -> "video/mp4",
    "ogg" -> "video/ogg",
    "pdf" -> "application/pdf",
    "png" -> "image/png",
    "ppt" -> "application/vnd.ms-powerpoint",
    "pptx" -> "application/vnd.ms-powerpoint",
    "ps" -> "application/postscript",
    "qt" -> "video/quicktime",
    "ra" -> "audio/vnd.rn-realaudio",
    "svg" -> "image/svg+xml",
    "tif" -> "image/tiff",
    "tiff" -> "image/tiff",
    "txt" -> "text/plain",
    "xls" -> "application/vnd.ms-excel",
    "xlsx" -> "application/vnd.ms-excel",
    "xml" -> "application/xml",
    "vcard" -> "text/vcard",
    "wav" -> "audio/vnd.wave",
    "webm" -> "video/webm",
    "wmv" -> "video/x-ms-wmv",
    "zip" -> "application/zip"
  ).withDefaultValue("application/octet-stream")

  def guessContentType(key: String): String = {
    val keyLC: String = key.substring(math.max(0, key.lastIndexOf('.') + 1)).trim.toLowerCase
    contentTypeMap(keyLC)
  }

  /** Helpful for debugging AWS requests and responses */
  def jsonPrettyPrint(awsObject: Object) = Json.prettyPrint(Json.parse(new JSONObject(awsObject).toString))

  def maybeCredentialsFromEnv: Option[AWSCredentials] = for {
    accessKey <- Some(System.getenv("AWS_ACCESS_KEY")) if accessKey.nonEmpty
    secretKey <- Some(System.getenv("AWS_SECRET_KEY")) if secretKey.nonEmpty
  } yield new com.amazonaws.auth.BasicAWSCredentials(accessKey, secretKey)

  // TODO make this read from ~/.aws/config instead of .s3
  def maybeCredentialsFromFile: Option[AWSCredentials] = for {
    s3File      <- Util.readS3File()
    credentials <- Util.getAuthentication(s3File.accountName)
  } yield  credentials

  /** UUID / GUID generator */
  def uuid = java.util.UUID.randomUUID.toString
}
