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

package com.micronautics.aws

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.cloudfront.AmazonCloudFrontClient
import com.amazonaws.services.cloudfront.model._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object CloudFront {
  def apply(implicit awsCredentials: AWSCredentials, cfClient: AmazonCloudFrontClient=new AmazonCloudFrontClient): CloudFront =
    new CloudFront()(awsCredentials, cfClient)
}

class CloudFront()(implicit val awsCredentials: AWSCredentials, val cfClient: AmazonCloudFrontClient=new AmazonCloudFrontClient) extends CFImplicits {
  import com.amazonaws.services.s3.model.Bucket

  def configs: List[DistributionSummary] = cfClient.listDistributions(new ListDistributionsRequest).getDistributionList.getItems.asScala.toList

  def distributions: List[DistributionSummary] = cfClient.listDistributions(new ListDistributionsRequest).getDistributionList.getItems.asScala.toList

  /** @return List of CloudFront distributions for the specified bucket */
  def distributionsFor(bucket: Bucket)(implicit s3: S3): List[DistributionSummary] = {
      val (_, bucketOriginId) = bucket.safeNames
      val distributionSummaries: List[DistributionSummary] = distributions.filter { distribution =>
        val origins: Seq[Origin] = distribution.getOrigins.getItems.asScala
        origins.filter(_.getId == bucketOriginId).nonEmpty
      }
      distributionSummaries
    }

  /** @return List of bucket names that have CloudFront distributions for this AWS account */
  // id.getDomainName value: s"$lcBucketName.s3.amazonaws.com"
  def bucketNamesWithDistributions: List[String] = for {
    distribution <- distributions
    item         <- distribution.getOrigins.getItems.asScala // value: s"S3-$lcBucketName"
  } yield item.getId.substring(3)

  def bucketsWithDistributions()(implicit s3: S3): List[Bucket] =
    for {
      bucketName <- bucketNamesWithDistributions
      bucket     <- s3.maybeBucketFor(bucketName).toList
  } yield bucket

  /** Invalidate asset in all bucket distributions where it is present.
    * @param assetPath The path of the objects to invalidate, relative to the distribution and must begin with a slash (/).
    *                  If the path is a directory, all assets within in are invalidated
    * @return number of asset invalidations */
  def invalidate(bucket: Bucket, assetPath: String)(implicit s3: S3): Int = invalidate(bucket, List(assetPath))

  /** Invalidate asset in all bucket distributions where it is present.
    * @param assetPaths The path of the objects to invalidate, relative to the distribution and must begin with a slash (/).
    *                   If the path is a directory, all assets within in are invalidated
    * @return number of asset invalidations
    * @see http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudfront/model/InvalidationBatch.html#InvalidationBatch(com.amazonaws.services.cloudfront.model.Paths,%20java.lang.String) */
  def invalidate(bucket: Bucket, assetPaths: List[String])(implicit s3: S3): Int = {
    val foundAssets: List[String] = assetPaths.filter(bucket.oneObjectData(_).isDefined)
    val foundPaths: Paths = new Paths().withItems(foundAssets.asJava).withQuantity(foundAssets.size)
    val counts: List[Int] = distributionsFor(bucket) map { distributionSummary =>
      import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest
      val invalidationBatch = new InvalidationBatch(foundPaths, uuid)
      // distributionSummary.getId returns distribution.getId
      cfClient.createInvalidation(new CreateInvalidationRequest().withDistributionId(distributionSummary.getId).withInvalidationBatch(invalidationBatch))
      1
    }
    counts.sum
  }

  def tryConfig(id: String): Try[DistributionConfig] = tryById(id).map(_.getDistributionConfig)

  def tryById(id: String): Try[Distribution] = try {
    Success(cfClient.getDistribution(new GetDistributionRequest().withId(id)).getDistribution)
  } catch {
    case nsde: NoSuchDistributionException =>
      Failure(new Exception(s"Distribution with id $id does not exist", nsde))

    case e: Exception =>
      Failure(new Exception(s"Distribution of with id $id: ${e.getMessage}", e))
  }
}

trait CFImplicits {
  import com.amazonaws.services.s3.model.Bucket

  object RichDistribution {
    var minimumCacheTime = 60 * 60 // one hour

    def apply(bucket: Bucket, priceClass: PriceClass=PriceClass.PriceClass_All, minimumCacheTime: Long=minimumCacheTime)
             (implicit awsCredentials: AWSCredentials, cfClient: AmazonCloudFrontClient, s3: S3): Distribution = {
      val (lcBucketName, bucketOriginId) = bucket.safeNames
      val aliases = new Aliases().withQuantity(0)
      val allowedMethods = new AllowedMethods().withItems(Method.GET, Method.HEAD).withQuantity(2)
      val cacheBehaviors = new CacheBehaviors().withQuantity(0)
      val cookiePreference = new CookiePreference().withForward(ItemSelection.All)
      val loggingConfig = new LoggingConfig()
        .withEnabled(false).withIncludeCookies(false)
        .withPrefix("").withBucket("")
      val s3OriginConfig = new S3OriginConfig().withOriginAccessIdentity("")
      val trustedSigners = new TrustedSigners().withEnabled(false).withQuantity(0)
      val forwardedValues = new ForwardedValues()
        .withCookies(cookiePreference)
        .withQueryString(false)
      val defaultCacheBehavior = new DefaultCacheBehavior()
        .withAllowedMethods(allowedMethods)
        .withForwardedValues(forwardedValues)
        .withMinTTL(minimumCacheTime)
        .withTargetOriginId(bucketOriginId)
        .withTrustedSigners(trustedSigners)
        .withViewerProtocolPolicy(ViewerProtocolPolicy.AllowAll)
      val origin = new Origin()
        .withDomainName(s"$lcBucketName.s3.amazonaws.com")
        .withS3OriginConfig(s3OriginConfig)
        .withId(bucketOriginId)
      val items = List(origin).asJava
      val origins = new Origins().withItems(items).withQuantity(items.size)
      val distributionConfig = new DistributionConfig()
        .withAliases(aliases)
        .withCacheBehaviors(cacheBehaviors)
        .withCallerReference(System.nanoTime.toString)
        .withComment("")
        .withDefaultCacheBehavior(defaultCacheBehavior)
        .withDefaultRootObject("index.html")
        .withEnabled(true)
        .withLogging(loggingConfig)
        .withOrigins(origins)
        .withPriceClass(priceClass)
      val cdr = new CreateDistributionRequest().withDistributionConfig(distributionConfig)
      //Logger.debug(s"cdr=${jsonPrettyPrint(cdr)}")
      try {
        val result: CreateDistributionResult = cfClient.createDistribution(cdr)
        result.getDistribution
      } catch {
        case nsoe: NoSuchOriginException =>
          throw new Exception(s"Origin with domain name '${origin.getDomainName}' and id '${origin.getId}' does not exist", nsoe)
      }
    }
  }
}
