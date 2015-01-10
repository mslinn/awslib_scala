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

import org.scalatest._
import org.scalatestplus.play._
import play.api.mvc.Results
import play.api.test._
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.language.postfixOps

class SNSTest extends PlaySpecServer {
  import play.api.libs.ws.WS
  import play.api.mvc._
  import play.api.test.FakeApplication

  // Override app if you need a FakeApplication with other than default parameters.
  implicit override lazy val app: FakeApplication =
    FakeApplication(
      //additionalConfiguration = Map("ehcacheplugin" -> "disabled"),
      withRoutes = {
        case ("GET", "/") =>
          Action { Ok("Got root") }

        case ("GET", "/lectures/sns/transcodingDone") =>
//          topic.publish("Hello from Amazon SNS!")
//          topic.delete()
          Action { Ok("Got callback") }
      }
    )

  implicit val snsClient = sns.snsClient
  val subscriberUrl = Option(System.getenv("TRANSCODER_SUBSCRIPTION_URL")).getOrElse("http://bear64.no-ip.info:9000")

  "blah" must {
    "test server logic" in {
      val myPublicAddress = s"localhost:$port"
      val testURL = s"http://$myPublicAddress"
      // The test payment gateway requires a callback to this server before it returns a result...
      val callbackURL = s"http://$myPublicAddress/callback"
      // await is from play.api.test.FutureAwaits
      val response = Await.result(WS.url(testURL).withQueryString("callbackURL" -> callbackURL).get(), 10 seconds)
      //response.status mustBe OK
    }
  }

  "SNS" must {
    "manipulate topics" in {
      sns.findOrCreateTopic("TestTopic").map { topic =>
        topic.subscribe(s"$subscriberUrl/lectures/sns/transcodingDone".asUrl)
      }.orElse { fail() }
      ()
    }
  }
}
