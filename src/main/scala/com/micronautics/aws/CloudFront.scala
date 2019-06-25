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
import com.amazonaws.services.cloudfront.{AmazonCloudFront, AmazonCloudFrontClientBuilder}
import com.amazonaws.services.cloudfront.model._
import com.micronautics.cache.{Memoizer, Memoizer0, Memoizer2}
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object CloudFront {
  val oneMinute: Long = 1L * 60L * 1000L

  def apply: CloudFront = new CloudFront
}

class CloudFront extends CFImplicits with S3Implicits {
  import CloudFront._
  import com.amazonaws.services.s3.model.Bucket

  implicit val cf: CloudFront = this
  implicit val cfClient: AmazonCloudFront = AmazonCloudFrontClientBuilder.standard.build

  val cacheIsDirty = new AtomicBoolean(false)

  protected val _distributions: Memoizer0[List[DistributionSummary]] =
    Memoizer(cfClient.listDistributions(new ListDistributionsRequest).getDistributionList.getItems.asScala.toList)

  val distributions: List[DistributionSummary] = _distributions.apply

  protected val _bucketNamesWithDistributions: Memoizer0[List[String]] =
    Memoizer(
      for {
        distribution <- distributions
        item         <- distribution.getOrigins.getItems.asScala // value: s"S3-$lcBucketName"
      } yield item.getId.substring(3)
    )

  /** @return List of bucket names that have CloudFront distributions for this AWS account */
  // id.getDomainName value: s"$lcBucketName.s3.amazonaws.com"
  val bucketNamesWithDistributions: List[String] =
    _bucketNamesWithDistributions.apply

  protected val _bucketsWithDistributions: Memoizer[S3, List[Bucket]] = Memoizer( s3 =>
    for {
      bucketName <- bucketNamesWithDistributions
      bucket     <- s3.findByName(bucketName).toList
    } yield bucket
  )

  def bucketsWithDistributions(implicit s3: S3): List[Bucket] =
    _bucketsWithDistributions(s3)

  /** @return List of CloudFront distributions for the specified bucket */
  protected val _distributionsFor: Memoizer2[Bucket, S3, List[DistributionSummary]] =
    Memoizer { (bucket, s3) =>
      val (_, bucketOriginId) = RichBucket(bucket)(s3).safeNames
      val distributionSummaries: List[DistributionSummary] = distributions.filter { distribution =>
        val origins: mutable.Seq[Origin] = distribution.getOrigins.getItems.asScala
        origins.exists(_.getId == bucketOriginId)
      }
      distributionSummaries
    }

  def distributionsFor(bucket: Bucket)(implicit s3: S3): List[DistributionSummary] =
    _distributionsFor.apply(bucket, s3)

  def clearCaches(): Unit = {
    _bucketNamesWithDistributions.clear()
    _bucketsWithDistributions.clear()
    _distributions.clear()
    _distributionsFor.clear()
    cacheIsDirty.set(false)
  }

  /** Enable/disable all distributions for the given bucketName
    * @return list of UpdateDistributionResult for distributions that were enabled */
  def enableAllDistributions(bucket: Bucket, newStatus: Boolean=true)(implicit s3: S3): List[UpdateDistributionResult] = {
    val distributions: List[DistributionSummary] = distributionsFor(bucket)
    distributions.flatMap { implicit distributionSummary =>
      val configResult: GetDistributionConfigResult = distributionSummary.configResult
      if (!configResult.getDistributionConfig.getEnabled) {
        configResult.getDistributionConfig.setEnabled(newStatus)
        val distributionETag = configResult.getETag // is the in the proper sequence?
        val updateRequest = new UpdateDistributionRequest(configResult.getDistributionConfig, distributionSummary.getId, distributionETag)
        val result = cfClient.updateDistribution(updateRequest)
        cacheIsDirty.set(true)
        Some(result)
      } else None
    }
  }

  /** Enable/disable the most recently created distribution for the given bucketName
    * @return Some(UpdateDistributionResult) if distributions was enabled, else None */
  def enableLastDistribution(bucket: Bucket, newStatus: Boolean=true)(implicit s3: S3): Option[UpdateDistributionResult] = {
    val distributions: Seq[DistributionSummary] = distributionsFor(bucket)
    distributions.lastOption.flatMap { implicit distributionSummary =>
      val configResult: GetDistributionConfigResult = distributionSummary.configResult
      if (!configResult.getDistributionConfig.getEnabled) {
        configResult.getDistributionConfig.setEnabled(newStatus)
        val distributionETag = configResult.getETag // is the in the proper sequence?
        val updateRequest = new UpdateDistributionRequest(configResult.getDistributionConfig, distributionSummary.getId, distributionETag)
        val result = cfClient.updateDistribution(updateRequest)
        cacheIsDirty.set(true)
        Some(result)
      } else None
    }
  }

  /** Invalidate asset in all bucket distributions where it is present.
    * @param assetPath The path of the objects to invalidate, relative to the distribution and must begin with a slash (/).
    *                  If the path is a directory, all assets within in are invalidated
    * @return number of asset invalidations */
  def invalidate(bucket: Bucket, assetPath: String)(implicit s3: S3): Int =
    invalidateMany(bucket, List(assetPath))

  /** Invalidate asset in all bucket distributions where it is present.
    * @param assetPaths The path of the objects to invalidate, relative to the distribution and must begin with a slash (/).
    *                   If the path is a directory, all assets within in are invalidated
    * @return number of asset invalidations
    * @see http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudfront/model/InvalidationBatch.html#InvalidationBatch(com.amazonaws.services.cloudfront.model.Paths,%20java.lang.String) */
  def invalidateMany(bucket: Bucket, assetPaths: List[String])(implicit s3: S3): Int = {
    val foundAssets: List[String] = assetPaths.filter(bucket.oneObjectData(_).isDefined)
    val foundPaths: Paths = new Paths().withItems(foundAssets.asJava).withQuantity(foundAssets.size)
    val counts: List[Int] = distributionsFor(bucket) map { distributionSummary =>
      import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest
      val invalidationBatch = new InvalidationBatch(foundPaths, uuid)
      // distributionSummary.getId returns distribution.getId
      cfClient.createInvalidation(new CreateInvalidationRequest().withDistributionId(distributionSummary.getId).withInvalidationBatch(invalidationBatch))
      1
    }
    val sum = counts.sum
    if (sum>0) cacheIsDirty.set(true)
    sum
  }

  /** Remove the most recently created distribution for the given bucketName.
    * Can take 15 minutes to an hour to return. */
  def removeDistribution(bucket: Bucket)(implicit s3: S3): Boolean =
    distributionsFor(bucket).lastOption.exists { implicit distSummary =>
      val distConfigResult = cfClient.getDistributionConfig(new GetDistributionConfigRequest().withId(distSummary.getId))
      val result = distSummary.originItems.map { removeOrigin(distSummary, distConfigResult, _) }.forall { _.isSuccess }
      if (result) cacheIsDirty.set(true)
      result
    }

  def removeOrigin(distSummary: DistributionSummary, distConfigResult: GetDistributionConfigResult, origin: Origin): Try[Boolean] = {
    val domainName: String = origin.getDomainName
    val distributionETag: String = distConfigResult.getETag
    val config: DistributionConfig = distConfigResult.getDistributionConfig
    // The explanation of how to find the eTag is wrong in the docs: http://docs.aws.amazon.com/AmazonCloudFront/latest/APIReference/DeleteDistribution.html
    // I think the correct explanation is that the eTag from the most recent GET or PUT operation is what is actually required
    val eTag = if (distConfigResult.getDistributionConfig.getEnabled) {
      Logger.debug(s"Disabling distribution of $domainName with id $$distributionId and ETag $distributionETag")
      config.setEnabled(false)
      Logger.debug(s"Distribution config after disabling=${ jsonPrettyPrint(config) }")
      val updateRequest = new UpdateDistributionRequest(config, distSummary.getId, distributionETag)
      val updateResult: UpdateDistributionResult = cfClient.updateDistribution(updateRequest)
      val updateETag = updateResult.getETag
      Logger.debug(s"Update result ETag = $updateETag; enabled=${ distSummary.enabled }; status=${ distSummary.status }")
      var i = 1
      while (distSummary.status == "InProgress") {
        Thread.sleep(oneMinute) // TODO don't tie up a thread like this
        Logger.debug(s"  $i: Distribution enabled=${ distSummary.enabled }; status=InProgress")
        i = i + 1
      }
      updateETag
    } else {
      Logger.debug(s"Distribution of $domainName with id ${ distSummary.getId } and ETag $distributionETag was already disabled.")
      distributionETag
    }
    // fails with: Distribution of scalacoursesdemo.s3.amazonaws.com with id E1ALVO6LY3X3XE and ETag E21ZQTZDDOETEA:
    // The distribution you are trying to delete has not been disabled.
    try {
      Logger.debug(s"Deleting distribution of $domainName with id ${ distSummary.getId } and ETag $eTag.")
      distSummary.delete(eTag)
      cacheIsDirty.set(true)
      Success(true)
    } catch {
      case nsde: NoSuchDistributionException =>
        Failure(nsde.prefixMsg(s"Distribution of $domainName with id ${ distSummary.getId } and ETag $distributionETag does not exist"))

      case e: Exception =>
        Failure(e.prefixMsg(s"Distribution of $domainName with id ${ distSummary.getId } and ETag $distributionETag: ${e.getMessage}"))
    }
  }

  def tryConfig(id: String): Try[DistributionConfig] = findDistributionById(id).map(_.getDistributionConfig)

  def findDistributionById(id: String): Try[Distribution] = try {
    Success(cfClient.getDistribution(new GetDistributionRequest().withId(id)).getDistribution)
  } catch {
    case nsde: NoSuchDistributionException =>
      Failure(nsde.prefixMsg(s"Distribution with id $id does not exist"))

    case e: Exception =>
      Failure(e.prefixMsg(s"Distribution of with id $id: ${ e.getMessage }"))
  }
}

trait CFImplicits {
  import com.amazonaws.services.s3.model.Bucket

  object RichDistribution {
    var minimumCacheTime: Long = 60 * 60 // one hour

    /** Create a new distribution for the given S3 bucket */
    def apply(
       bucket: Bucket,
       priceClass: PriceClass=PriceClass.PriceClass_All,
       minimumCacheTime: Long=minimumCacheTime
     )(implicit
       awsCredentials: AWSCredentials,
       cfClient: AmazonCloudFront,
       s3: S3
    ): Distribution = {
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
          throw new Exception(s"Origin with domain name '${ origin.getDomainName }' and id '${ origin.getId }' does not exist", nsoe)
      }
    }
  }

  implicit class RichDistributionSummary(distributionSummary: DistributionSummary)(implicit cfClient: AmazonCloudFront) {
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
      Logger.debug(s"deleteDistributionRequest=${ jsonPrettyPrint(deleteDistributionRequest) }")
      cfClient.deleteDistribution(deleteDistributionRequest)
      ()
    }

    def eTag: String = configResult.getETag

    def originItems: List[Origin] = config.getOrigins.getItems.asScala.toList

    def removeOrigin(distConfigResult: GetDistributionConfigResult, origin: Origin)(implicit cf: CloudFront): Try[Boolean] =
      cf.removeOrigin(distributionSummary, distConfigResult, origin)

    def status: String = {
      Logger.debug(s"Getting status from distributionSummary with id ${ distributionSummary.getId }")
      distributionSummary.getStatus
    }
  }
}
