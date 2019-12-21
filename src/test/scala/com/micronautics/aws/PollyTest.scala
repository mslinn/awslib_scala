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
import com.amazonaws.services.polly.model.Voice

class PollyTest extends WordSpec with TestBase {
  val text: String =
    """This is a paragraph.
      |It has no feelings.
      |No feelings at all.
      |Cherie, je t'aime!
      |Very sad indeed!
      |Yet there is joy in my virtual heart.
      |For I do not live on Earth, the Moon, or in the stars.
      |""".stripMargin

  "Polly" must {
    "describe voices" in {
      val englishVoices: List[Voice] = polly.describeVoices()
      englishVoices.size must be > 0

      val frenchVoices: List[Voice] = polly.describeVoices("fr-FR")
      frenchVoices.size must be > 0
    }

    "find all regional lexicons" in {
      val lexicons = polly.regionalLexicons
      println(lexicons)
      lexicons.size mustBe 0
    }

    "find lexicon" in {
      polly.lexiconFor("asdf") match {
        case Left(lexicon) => fail(s"Lexicon with name asdf must not be found because it does not exist")
        case Right(e) =>
      }
    }
  }
}
