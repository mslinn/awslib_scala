package com.micronautics.aws

import java.io.File
import java.util.Date
import com.amazonaws.auth.policy.Principal
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfter, WordSpec}
import org.scalatest.matchers.MustMatchers
import scala.Some
import com.amazonaws.services.s3.model.{SetBucketAclRequest, CanonicalGrantee, AccessControlList, Permission, GetBucketAclRequest, Bucket}

/**These tests will fail unless a file called AwsCredentials.properties is created in src/test/resources. */
class S3Test extends WordSpec with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {
  val bucketName = "test" + new Date().getTime
  val file1Name = "index.html"
  val file2Name = "index2.html"
  val s3File1: S3File = Util.readS3File
  val file1 = new File(file1Name)
  val file2 = new File(file2Name)
  val creds = Util.getAuthentication(s3File1.accountName)
  var bucket: Bucket = null

  val s3: S3 = creds match {
    case Some(credentials) =>
      S3Model.credentials = credentials
      new S3(credentials.accessKey, credentials.secretKey)

    case None =>
      fail("Cannot locate .com.micronautics.aws file")
  }
  val s3File = s3File1.copy(bucketName=this.bucketName)

  override def afterAll() {
    s3.deleteBucket(bucketName)
    if (file2.exists)
      file2.delete
    bucket = null
  }

  override def beforeAll() {
    println("Creating bucket " + bucketName)
    bucket = s3.createBucket(bucketName)
  }

  "Bucket names" must {
    "not contain invalid characters" in {
      val bnSanitized: String = "Blah ick! ro!x@3.".toLowerCase.replaceAll("[^A-Za-z0-9.]", "")
      assert(bnSanitized==="blahickrox3.", "Invalid characters removed")
    }

    "not duplicate existing bucket names" in {
      assert(s3.bucketExists(bucketName), "Bucket should exists")
    }
  }

  "Bucket websites" must {
    "be queryable" in {
      // These asserts require the system property: -Dcom.amazonaws.sdk.disableCertChecking=true
      assert(s3.isWebsiteEnabled("www.mslinn.com"))
      assert(s3.isWebsiteEnabled("www.slinnbooks.com"))
      assert(!s3.isWebsiteEnabled(bucketName))
    }
  }

  "ACLs" must {
    "provide access to authorized people" in {
      val getBucketAclRequest = new GetBucketAclRequest(bucketName)
      println(getBucketAclRequest)

      val principal = new Principal("3be599c1fa2d0ef24de229ad27adb107f736a79727ef8753fba31ff7db10e2ee")
      val accessControlList = new AccessControlList()
      val grantee = new CanonicalGrantee("mslinn@mslinn.com")
      accessControlList.grantPermission(grantee, Permission.FullControl)
      val setBucketAclRequest = new SetBucketAclRequest(bucketName, accessControlList)
      println(setBucketAclRequest)
    }
  }

  "S3 operations" must {
    "ensure file to upload can be found" in {
      assert(file1.exists, "Ensure file to upload can be found")
    }

    "pretty-print JSON" in {
      val contents = """{"accountName":"memyselfi","bucketName":"blah","ignores":[".*~",".*.com.micronautics.aws",".*.git",".*.s3",".*.svn",".*.tmp","cvs"],"endpoint":"s3-website-us-east-1.amazonaws.com"}"""
      val result = contents.replaceAll("(.*?:(\\[.*?\\],|.*?,))", "$0\n ")
      println(result)
      assert(
        """{"accountName":"memyselfi",
          | "bucketName":"blah",
          | "ignores":[".*~",".*.com.micronautics.aws",".*.git",".*.s3",".*.svn",".*.swp",".*.tmp","cvs"],
          | "endpoint":"s3-website-us-east-1.amazonaws.com"}""".stripMargin === result, "PrettyPrinted JSON")
    }
  }
}
