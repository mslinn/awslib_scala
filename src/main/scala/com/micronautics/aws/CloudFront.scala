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
import AwsCredentials.Logger

object CloudFront {
  val oneMinute = 1L * 60L * 1000L

  def apply(implicit awsCredentials: AWSCredentials, cfClient: AmazonCloudFrontClient=new AmazonCloudFrontClient): CloudFront =
    new CloudFront()(awsCredentials, cfClient)
}

class CloudFront()(implicit val awsCredentials: AWSCredentials, val cfClient: AmazonCloudFrontClient=new AmazonCloudFrontClient) extends CFImplicits {
  import CloudFront._
  import com.amazonaws.services.s3.model.Bucket

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

  def bucketsWithDistributions(implicit s3: S3): List[Bucket] =
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

  /** Enable/disable all distributions for the given bucketName */
  def enableAllDistributions(bucket: Bucket, newStatus: Boolean=true)(implicit s3: S3): List[UpdateDistributionResult] = {
    val distributions: List[DistributionSummary] = distributionsFor(bucket)
    distributions.map { implicit distributionSummary =>
        val configResult: GetDistributionConfigResult = distributionSummary.configResult
        configResult.getDistributionConfig.setEnabled(newStatus)
        val distributionETag = configResult.getETag // is the in the proper sequence?
        val updateRequest = new UpdateDistributionRequest(configResult.getDistributionConfig, distributionSummary.getId, distributionETag)
        cfClient.updateDistribution(updateRequest)
    }
  }

  /** Enable/disable the most recently created distribution for the given bucketName */
  def enableLastDistribution(bucket: Bucket, newStatus: Boolean=true)(implicit s3: S3): Option[UpdateDistributionResult] = {
    val distributions: Seq[DistributionSummary] = distributionsFor(bucket)
    distributions.lastOption.map { implicit distributionSummary =>
        val configResult: GetDistributionConfigResult = distributionSummary.configResult
        configResult.getDistributionConfig.setEnabled(newStatus)
        val distributionETag = configResult.getETag // is the in the proper sequence?
        val updateRequest = new UpdateDistributionRequest(configResult.getDistributionConfig, distributionSummary.getId, distributionETag)
        cfClient.updateDistribution(updateRequest)
    }
  }

  /** Remove the most recently created distribution for the given bucketName.
    * Can take 15 minutes to an hour to return. */
  def removeDistribution(bucket: Bucket)(implicit s3: S3): Boolean =
    distributionsFor(bucket).lastOption.exists { implicit distSummary =>
      val distributionId = distSummary.getId
      val distConfigResult = cfClient.getDistributionConfig(new GetDistributionConfigRequest().withId(distSummary.getId))
      distSummary.originItems.map { removeOrigin(distSummary, distributionId, distConfigResult, _) }.forall { _.isSuccess }
    }

  def removeOrigin(distSummary: DistributionSummary, distributionId: String, distConfigResult: GetDistributionConfigResult, origin: Origin): Try[Boolean] = {
    val domainName: String = origin.getDomainName
    val distributionETag: String = distConfigResult.getETag
    val config: DistributionConfig = distConfigResult.getDistributionConfig
    // The explanation of how to find the eTag is wrong in the docs: http://docs.aws.amazon.com/AmazonCloudFront/latest/APIReference/DeleteDistribution.html
    // I think the correct explanation is that the eTag from the most recent GET or PUT operation is what is actually required
    val eTag = if (distConfigResult.getDistributionConfig.getEnabled) {
      Logger.debug(s"Disabling distribution of $domainName with id $$distributionId and ETag $distributionETag")
      config.setEnabled(false)
      Logger.debug(s"Distribution config after disabling=${jsonPrettyPrint(config)}")
      val updateRequest = new UpdateDistributionRequest(config, distributionId, distributionETag)
      val updateResult: UpdateDistributionResult = cfClient.updateDistribution(updateRequest)
      val updateETag = updateResult.getETag
      Logger.debug(s"Update result ETag = $updateETag; enabled=${distSummary.enabled}; status=${distSummary.status}")
      var i = 1
      while (distSummary.status == "InProgress") {
        Thread.sleep(oneMinute) // TODO don't tie up a thread like this
        Logger.debug(s"  $i: Distribution enabled=${distSummary.enabled}; status=InProgress")
        i = i + 1
      }
      updateETag
    } else {
      Logger.debug(s"Distribution of $domainName with id $distributionId and ETag $distributionETag was already disabled.")
      distributionETag
    }
    // fails with: Distribution of scalacoursesdemo.s3.amazonaws.com with id E1ALVO6LY3X3XE and ETag E21ZQTZDDOETEA:
    // The distribution you are trying to delete has not been disabled.
    try {
      Logger.debug(s"Deleting distribution of $domainName with id $distributionId and ETag $eTag.")
      distSummary.delete(eTag)
      Success(true)
    } catch {
      case nsde: NoSuchDistributionException =>
        Failure(new Exception(s"Distribution of $domainName with id $distributionId and ETag $distributionETag does not exist", nsde))

      case e: Exception =>
        Failure(new Exception(s"Distribution of $domainName with id $distributionId and ETag $distributionETag: ${e.getMessage}", e))
    }
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
      Logger.debug(s"cdr=${jsonPrettyPrint(cdr)}")
      try {
        val result: CreateDistributionResult = cfClient.createDistribution(cdr)
        result.getDistribution
      } catch {
        case nsoe: NoSuchOriginException =>
          throw new Exception(s"Origin with domain name '${origin.getDomainName}' and id '${origin.getId}' does not exist", nsoe)
      }
    }
  }

  implicit class RichDistributionSummary(distributionSummary: DistributionSummary)(implicit cfClient: AmazonCloudFrontClient) {
    def config: DistributionConfig = configResult.getDistributionConfig

    def configResult: GetDistributionConfigResult = {
      val getDistributionConfigRequest = new GetDistributionConfigRequest().withId(distributionSummary.getId)
      cfClient.getDistributionConfig(getDistributionConfigRequest)
    }

    def enabled: Boolean = {
      Logger.debug("Getting enabled from distributionSummary")
      distributionSummary.getEnabled
    }

    def delete(eTag: String): Unit = {
      Logger.debug(s"Deleting distribution with id ${distributionSummary.getId}")
      val deleteDistributionRequest = new DeleteDistributionRequest().withId(distributionSummary.getId).withIfMatch(eTag)
      Logger.debug(s"deleteDistributionRequest=${jsonPrettyPrint(deleteDistributionRequest)}")
      cfClient.deleteDistribution(deleteDistributionRequest)
    }

    def eTag: String = configResult.getETag

    def originItems: List[Origin] = config.getOrigins.getItems.asScala.toList

    def status: String = {
      Logger.debug(s"Getting status from distributionSummary with id ${distributionSummary.getId}")
      distributionSummary.getStatus
    }
  }
}
