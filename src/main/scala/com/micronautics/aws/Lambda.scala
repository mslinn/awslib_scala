/* Copyright 2012-2016 Micronautics Research Corporation.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License. */

package com.micronautics.aws

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.lambda.{AWSLambda, AWSLambdaClient}

object Lambda {
  def apply(implicit awsCredentials: AWSCredentials): Lambda = new Lambda()(awsCredentials)
}

class Lambda()(implicit val awsCredentials: AWSCredentials) {
  implicit val lambda: Lambda = this
  implicit val lambdaClient: AWSLambdaClient = new AWSLambdaClient(awsCredentials)
}

trait LambdaImplicits {
  implicit class RichLambda(val lambdaClient: AWSLambdaClient)(implicit lambda: AWSLambda) {
    // nothing yet
  }
}
