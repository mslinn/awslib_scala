/* Copyright 2012-2015 Micronautics Research Corporation.
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

import org.scalatest.WordSpec

class TestR53 extends WordSpec with TestBase {
  "CNAME Aliases" must {
    "work" in {
      val aliasName = "test123"
      try { // delete if it already exists from the last time
        r53.deleteCnameAlias(s"$aliasName.scalacourses.com")
      } catch {
        case _: Error =>
      }
      r53.createCnameAlias("scalacourses.com", aliasName, "www.scalacourses.com")

      r53.clearCaches()
      val rSets = r53.recordSets("scalacourses.com")
      val exists = rSets.exists { _.getName.startsWith(aliasName) }
      assert(exists, s"CNAME alias $aliasName exists")

      r53.deleteCnameAlias(s"$aliasName.scalacourses.com")
      r53.clearCaches()
      val rSets2 = r53.recordSets("scalacourses.com")
      val exists2 = rSets2.exists { _.getName.startsWith(aliasName) }
      assert(!exists2, s"CNAME alias $aliasName does not exist")

      r53.clearCaches()
    }
  }
}
