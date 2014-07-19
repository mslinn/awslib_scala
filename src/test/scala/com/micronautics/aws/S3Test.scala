package com.micronautics.aws

import com.amazonaws.services.s3.model.{SetBucketAclRequest, CanonicalGrantee, AccessControlList, Permission, GetBucketAclRequest, Bucket}
import java.io.File
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfter, WordSpec, Matchers}

class S3Test extends WordSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with Fixtures {
  val file1Name = "index.html"
  val file2Name = "index2.html"
  val file1 = new File(file1Name)
  val file2 = new File(file2Name)

  override def beforeAll(): Unit = {
    super.beforeAll()
    file1.createNewFile()
    file2.createNewFile()
    ()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    if (file1.exists)
      file1.delete
    if (file2.exists)
      file2.delete
    ()
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
          | "ignores":[".*~",".*.com.micronautics.aws",".*.git",".*.s3",".*.svn",".*.tmp","cvs"],
          | "endpoint":"s3-website-us-east-1.amazonaws.com"}""".stripMargin === result, "PrettyPrinted JSON")
    }
  }
}
