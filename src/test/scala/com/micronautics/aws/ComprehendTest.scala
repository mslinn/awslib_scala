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

import com.amazonaws.services.comprehend.model.{DetectSentimentResult, DominantLanguage, Entity, KeyPhrase, SyntaxToken}
import org.scalatest.WordSpec

class ComprehendTest extends WordSpec with TestBase {
  val text: String =
    """This is a paragraph.
      |It has no feelings.
      |No feelings at all.
      |Chérie, je t'aime!
      |Very sad indeed!
      |Yet there is joy in my virtual heart.
      |For I do not live on Earth, the Moon, or in the stars.
      |""".stripMargin

  "Comprehend" must {
    "detect all languages used" in {
      val actual: List[DominantLanguage] = comprehend.detectDominantLanguages(text)
      actual mustBe List("en", "fr")
    }

    "detect the most common language used" in {
      val actual: DominantLanguage = comprehend.detectDominantLanguage(text)
      actual.getLanguageCode mustBe "en"
      actual.getScore mustBe 1.0f
    }

    "detect entities" in {
      val actual: List[Entity] = comprehend.detectEntities(text)
      actual.size must be > 0
    }

    "detect key phrases" in {
      val actual: List[KeyPhrase] = comprehend.detectKeyPhrases(text)
      actual.size must be > 0
    }

    "detect sentiment" in {
      val actual: DetectSentimentResult = comprehend.detectSentiment(text)
      actual.getSentiment mustBe "NEUTRAL"
      val score = actual.getSentimentScore
      score.getMixed mustBe > 0
      score.getNegative mustBe > 0
      score.getNeutral mustBe > 0
      score.getPositive mustBe > 0
    }

    "detect syntax" in {
      val actual: List[SyntaxToken] = comprehend.detectSyntax(text)
      actual.size must be > 0
    }
  }
}
