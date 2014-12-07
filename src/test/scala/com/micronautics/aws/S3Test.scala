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

import java.io.File
import com.micronautics.aws.S3._
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpec}

class S3Test extends WordSpec with Init with Matchers with BeforeAndAfter with BeforeAndAfterAll with Fixtures {
  val file1Name = "index.html"
  val file2Name = "index2.html"
  val file1 = new File(file1Name)
  val file2 = new File(file2Name)

  val publisher1UserId = "testPublisher1UserId"
  val instructor1UserId = "testInstructor1UserId"
  val instructor2UserId = "testInstructor2UserId"

  private def saveToFile(file: java.io.File, string: String): Unit = {
    val printWriter = new java.io.PrintWriter(file)
    try {
      printWriter.print(string)
    } finally {
      printWriter.close()
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    saveToFile(file1, """<h1>Test 1 of AWS S3</h1>
                        |<p>This is index.html</p>
                        |""".stripMargin)
    saveToFile(file2, """<h1>Test 2 of AWS S3</h1>
                        |<p>This is index2.html</p>
                        |""".stripMargin)
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
      assert(s3.bucketExists(bucketName), "Bucket should exist")
    }
  }

  "Buckets" must {
    "be queryable" in {
      // These asserts require the system property: -Dcom.amazonaws.sdk.disableCertChecking=true
      assert(s3.isWebsiteEnabled(bucketName))
      assert(s3.bucketExists(bucketName))
      assert(!s3.bucketExists("aosdifaosidfyoasidfyoasidfyuoasidfyosiadfyoi"))
    }

    "prevent duplicate bucket names" in {
      val thrown = intercept[Exception] {
        s3.createBucket(bucketName)
      }
      assert(thrown.getMessage contains "exists")
    }

    "Manage policies" in {
      assert(bucket.policyAsJson == s"""{"Version":"2008-10-17","Statement":[{"Sid":"AddPerm","Effect":"Allow","Principal":{"AWS":"*"},"Action":"s3:GetObject","Resource":"arn:aws:s3:::$bucketName/*"}]}""")

      /** No principals for the publisher or any instructors are referenced */
      val cadenzaPolicy = s"""|{
      |	"Version": "2012-10-17",
      |	"Statement": [
      |		{
      |			"Sid": "Allow root and publisher ($publisher1UserId) to do everything",
      |			"Effect": "Allow",
      |			"Principal": {
      |				"AWS": [
      |					"arn:aws:iam::031372724784:root",
      |					"arn:aws:iam::031372724784:user/root"
      |				]
      |			},
      |			"Action": "s3:*",
      |			"Resource": "arn:aws:s3:::$bucketName/*"
      |		},
      |		{
      |			"Sid": "Publisher and instructors can ListBucket",
      |			"Effect": "Allow",
      |			"Principal": {
      |				"AWS": [
      |					"arn:aws:iam::031372724784:root",
      |					"arn:aws:iam::031372724784:user/root",
      |					"arn:aws:iam::031372724784:user/superuser"
      |				]
      |			},
      |			"Action": "s3:ListBucket",
      |			"Resource": "arn:aws:s3:::$bucketName"
      |		},
      |		{
      |			"Sid": "AddPerm",
      |			"Effect": "Allow",
      |			"Principal": {
      |				"AWS": "*"
      |			},
      |			"Action": "s3:GetObject",
      |			"Resource": "arn:aws:s3:::$bucketName/*"
      |		}
      |	]
      |}""".stripMargin
      bucket.policy = cadenzaPolicy // if the policy is accepted then all is well
    }

    "be able to do lots of things" in {
      bucket.uploadString("you/know/that/this/means/war.txt", "blah blah")
      bucket.uploadString("you/know/doodle.txt", "deedle doodle")
      val contents1 = bucket.downloadAsString("you/know/that/this/means/war.txt")
      assert(contents1=="blah blah")
      assert(bucket.listObjectsByPrefix("you").size == 2)
      assert(bucket.allObjectData("you/know").size==2)
      assert(bucket.allObjectData("").size==2)

      bucket.deletePrefix("you/know/that")
      assert(bucket.listObjectsByPrefix("you").size == 1)
      assert(bucket.allObjectData("you/know").size==1)
      assert(bucket.allObjectData("").size==1)

      val contents2 = bucket.downloadAsString("you/know/doodle.txt")
      assert(contents2=="deedle doodle")

      bucket.deleteObject("you/know/doodle.txt")
      assert(bucket.allObjectData("").size==0)

      assert(bucket.isWebsiteEnabled)
      bucket.disableWebsite()
      assert(!bucket.isWebsiteEnabled)
      bucket.enableWebsite("error.html")
      assert(bucket.isWebsiteEnabled)

      bucket.enableCors()
      bucket.uploadString("you/know/that/this/means/war.html", "blah blah")
      bucket.uploadString("you/know/doodle.txt", "deedle doodle")
      assert(bucket.allObjectData("").size==2)

      bucket.oneObjectData("you/know/doodle.txt").exists{ _.getKey == "you/know/doodle.txt" }

      assert(bucket.resourceUrl("you/know/doodle.txt") == s"https://$bucketName.s3.amazonaws.com/you/know/doodle.txt")

      assert(s3.setContentType("plain.txt").getContentType=="text/plain")
      assert(s3.setContentType("page.html").getContentType=="text/html")
      assert(s3.setContentType("video.mp4").getContentType=="video/mp4")

      bucket.move("you/know/doodle.txt", "autre/location/blah.txt")
      val contents3 = bucket.downloadAsString("autre/location/blah.txt")
      assert(contents3 == "deedle doodle")
      val thrown = intercept[Exception] {
        bucket.downloadAsString("you/know/doodle.txt")
      }

      bucket.uploadFile(file1Name, file1)
      assert(bucket.allObjectData(file1Name).size==1)

      bucket.uploadFileOrDirectory(file2Name, file2)
      assert(bucket.allObjectData(file2Name).size==1)

      bucket.empty()
      assert(bucket.allObjectData("").size==0)

      assert(s3.bucketNames contains bucket.getName)
    }
  }

 /* "ACLs" must {
    "provide access to authorized people" ignore {
      val getBucketAclRequest = new GetBucketAclRequest(bucketName)
      println(getBucketAclRequest)

      val accessControlList = new AccessControlList()
      val grantee = new CanonicalGrantee("mslinn@mslinn.com")
      accessControlList.grantPermission(grantee, Permission.FullControl)
      val setBucketAclRequest = new SetBucketAclRequest(bucketName, accessControlList)
      println(setBucketAclRequest)
    }
  }*/

  "S3 key names" must {
    "be sanitized" in {
      assert(sanitizePrefix("/this/means/war.txt")=="this/means/war.txt")
      assert(relativize("/of/course/you/realize/that/this/means/war.txt")=="of/course/you/realize/that/this/means/war.txt")
      assert(sanitizePrefix("this/means/war.txt")=="this/means/war.txt")
      assert(relativize("this/means/war.txt")=="this/means/war.txt")
    }
  }

  "S3 operations" must {
    "ensure file to upload can be found" in {
      assert(file1.exists, "Ensure file to upload can be found")
    }

    "pretty-print JSON" in {
      val contents = """{"accountName":"memyselfi","bucketName":"blah","ignores":[".*~",".*.com.micronautics.aws",".*.git",".*.s3",".*.svn",".*.tmp","cvs"],"endpoint":"s3-website-us-east-1.amazonaws.com"}"""
      val result = contents.replaceAll("(.*?:(\\[.*?\\],|.*?,))", "$0\n ")
      assert(
        """{"accountName":"memyselfi",
          | "bucketName":"blah",
          | "ignores":[".*~",".*.com.micronautics.aws",".*.git",".*.s3",".*.svn",".*.tmp","cvs"],
          | "endpoint":"s3-website-us-east-1.amazonaws.com"}""".stripMargin === result, "PrettyPrinted JSON")
    }
  }
}
