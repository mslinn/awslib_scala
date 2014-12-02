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

package com.micronautics.aws

import java.io.File

import com.amazonaws.services.s3.model.{AccessControlList, CanonicalGrantee, GetBucketAclRequest, Permission, SetBucketAclRequest}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpec}

class S3Test extends WordSpec with Matchers with BeforeAndAfter with BeforeAndAfterAll with Fixtures {
  val file1Name = "index.html"
  val file2Name = "index2.html"
  val file1 = new File(file1Name)
  val file2 = new File(file2Name)

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

  "Bucket websites" must {
    "be queryable" in {
      // These asserts require the system property: -Dcom.amazonaws.sdk.disableCertChecking=true
      assert(s3.isWebsiteEnabled("www.mslinn.com"))
      assert(s3.isWebsiteEnabled("www.slinnbooks.com"))
      assert(!s3.isWebsiteEnabled(bucketName))
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
