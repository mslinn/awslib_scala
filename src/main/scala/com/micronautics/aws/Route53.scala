package com.micronautics.aws

import java.util
import java.util.concurrent.atomic.AtomicBoolean
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.route53.model._
import com.micronautics.cache.{Memoizer, Memoizer0}
import collection.JavaConverters._
import com.amazonaws.services.route53.AmazonRoute53Client

class Route53()(implicit val awsCredentials: AWSCredentials) {
  implicit val r53 = this
  implicit val r53Client = new AmazonRoute53Client(awsCredentials)

  val cacheIsDirty = new AtomicBoolean(false)

  def clearCaches(): Unit = {
    _hostedZones.clear()
    _recordSets.clear()
    cacheIsDirty.set(false)
  }

  def aliasExists(dnsNameStart: String)(implicit rSets: List[ResourceRecordSet]): Boolean =
    rSets.exists(rSet => rSet.getType=="CNAME" && rSet.getName.startsWith(dnsNameStart))

  /** @param hostedZoneName such as "scalacourses.com" (a trailing period will be appended as required)
    * @param dnsName goes in the Name field, and is concatenated with hostedZoneName (so "test" is interpreted to mean "test.scalacourses.com")
    * @param cname external resource to link to ("xxx.herokuapp.com.") - note the trailing period, might not be necessary
    * @param routingPolicy not used yet
    * @param ttl time to live, in seconds */
  def createCnameAlias(hostedZoneName: String, dnsName: String, cname: String, routingPolicy: String="Simple", ttl: Long=60L): Boolean = {
    maybeHostedZone(hostedZoneName).exists { hostedZone =>
      implicit val rSets: List[ResourceRecordSet] = recordSets(hostedZone + ".")
      val dnsNameStart = dnsName + "."
      val aRecord: Boolean = rSets.exists(_.getName.startsWith(dnsNameStart))
      if (aRecord || aliasExists(dnsNameStart))
        false // Record already exists with the given dnsName, return failure
      else {
        val resourceRecords = List(new ResourceRecord().withValue(cname)).asJava
        val newResourceRecordSet: ResourceRecordSet = new ResourceRecordSet()
          .withName(s"$dnsName.$hostedZoneName")
          .withType(RRType.CNAME)
          .withTTL(ttl)
          .withResourceRecords(resourceRecords)
        val changeBatch = new ChangeBatch(List(new Change(ChangeAction.CREATE, newResourceRecordSet)).asJava)
        r53Client.changeResourceRecordSets(new ChangeResourceRecordSetsRequest(hostedZone.getId, changeBatch))
        cacheIsDirty.set(true)
        true
      }
    }
  }

  /** @return true if the alias was found and deleted */
  def deleteCnameAlias(alias: String, evaluateTargetHealth: Boolean = false): Boolean = {
    val dot = alias.indexOf(".")
    assert(dot>0)
    val dnsName = alias.substring(0, dot)
    val hostedZoneName = alias.substring(dot+1)
    maybeHostedZone(hostedZoneName).exists { hostedZone =>
      implicit val resourceRecordSets: List[ResourceRecordSet] = recordSets(hostedZone)
      val dnsNameStart = dnsName + "."
      val gotSome = for {
        resourceRecordSet <- resourceRecordSets
        if resourceRecordSet.getName.startsWith(dnsNameStart)
        if aliasExists(dnsNameStart)
      } yield {
        val changeBatch = new ChangeBatch(List(new Change(ChangeAction.DELETE, resourceRecordSet)).asJava)
        val changeResourceRecordSetsRequest = new ChangeResourceRecordSetsRequest(hostedZone.getId, changeBatch)
        r53Client.changeResourceRecordSets(changeResourceRecordSetsRequest)
      }
      cacheIsDirty.set(gotSome.nonEmpty)
      gotSome.nonEmpty
    }
  }

  protected val _hostedZones: Memoizer0[List[HostedZone]] =
    Memoizer(r53Client.listHostedZones().getHostedZones.asScala.toList)

  /** **cached** */
  val hostedZones: List[HostedZone] = _hostedZones.apply


  /** @param name is checked after a dot is appended */
  def maybeHostedZone(name: String): Option[HostedZone] =
    hostedZones.find(_.getName==name + ".")

  protected val _recordSets: Memoizer[HostedZone, List[ResourceRecordSet]] = Memoizer( hostedZone =>
    {
      def getEmAll(lrrsr: ListResourceRecordSetsResult, accum: List[ResourceRecordSet]=Nil): List[ResourceRecordSet] =
        if (!lrrsr.getIsTruncated) accum ::: lrrsr.getResourceRecordSets.asScala.toList else {
          lrrsr.getNextRecordName
          getEmAll(lrrsr, accum ::: lrrsr.getResourceRecordSets.asScala.toList)
        }

      val lrrsr: ListResourceRecordSetsResult =
        r53Client.listResourceRecordSets(new ListResourceRecordSetsRequest(hostedZone.getId))
      val rrss: List[ResourceRecordSet] = getEmAll(lrrsr)
      rrss
    }
  )

  def recordSets(hostedZone: HostedZone): List[ResourceRecordSet] =
    _recordSets.apply(hostedZone)

  def recordSets(hostedZoneName: String): List[ResourceRecordSet] =
    maybeHostedZone(hostedZoneName).map { hostedZone =>
      recordSets(hostedZone)
    }.getOrElse(Nil)
}
