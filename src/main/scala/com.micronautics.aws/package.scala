/* Copyright 2012 Micronautics Research Corporation.
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

import collection.mutable.MutableList
import scala.Array
import org.joda.time.DateTime
import java.util.regex.Pattern
import org.codehaus.jackson.annotate.JsonIgnore
import collection.JavaConversions._

case class Credentials(awsAccountName: String, accessKey: String, secretKey: String)

class AllCredentials extends MutableList[Credentials] {
  def addAll(credArray: Array[Credentials]): AllCredentials = {
    credArray foreach { credentials => this += credentials }
    this
  }

  def defines(accountName: String): Boolean = groupBy(_.awsAccountName).keySet.contains(accountName)
}

object AllCredentials {
  def apply(credArray: Array[Credentials]): AllCredentials = {
    val allCredentials = new AllCredentials()
    allCredentials.addAll(credArray)
  }
}

object AWS {
  // todo provide user-friendly means to edit the .s3 file regexes
  /** Regexes; these get saved to .s3 files */
  val defaultIgnores = Seq(".*~", ".*.com.micronautics.aws", ".*.git", ".*.s3", ".*.svn", ".*.swp", ".*.tmp", "cvs")
  var allCredentials = new AllCredentials()
}

object AuthAction extends Enumeration {
   type AuthAction = Value
   val add, delete, list, modify = Value
 }

case class S3File(accountName: String,
                  bucketName: String,
                  lastSyncOption: Option[DateTime]=None,
                  ignores: Seq[String]=AWS.defaultIgnores,
                  endpoint: String = ".s3.amazonaws.com") {
  @JsonIgnore val ignoredPatterns: Seq[Pattern] = ignores.map { x => Pattern.compile(x) }

  @JsonIgnore def endpointUrl: String = "https://" + bucketName + "." + endpoint;
}
