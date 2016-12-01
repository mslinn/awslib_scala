/* Copyright 2012-2016 Micronautics Research Corporation.
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
import com.amazonaws.services.rekognition.AmazonRekognitionAsyncClient

object Rekognition {
  def apply(implicit awsCredentials: AWSCredentials): Rekognition = new Rekognition()(awsCredentials)
}

class Rekognition()(implicit val awsCredentials: AWSCredentials) {
  implicit val rekog: Rekognition = this
  implicit val rekogClient: AmazonRekognitionAsyncClient = new AmazonRekognitionAsyncClient(awsCredentials)

}

trait RekognitionImplicits {
}
