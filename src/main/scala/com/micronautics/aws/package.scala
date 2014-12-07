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

import com.amazonaws.auth.BasicAWSCredentials
import scala.util.control.NoStackTrace

case class Credentials(awsAccountName: String, accessKey: String, secretKey: String) extends BasicAWSCredentials(accessKey, secretKey) {
  lazy val asBasicAWSCredentials: BasicAWSCredentials = new BasicAWSCredentials(accessKey, secretKey)
}

class ExceptTrace(msg: String) extends Exception(msg) with NoStackTrace

object ExceptTrace {
  def apply(msg: String): ExceptTrace = new ExceptTrace(msg)

  def apply(msg: String, exception: Exception): ExceptTrace = new ExceptTrace(msg)
}
