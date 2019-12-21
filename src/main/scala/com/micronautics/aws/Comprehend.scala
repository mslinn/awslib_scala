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

import com.amazonaws.services.comprehend.model.{DetectSentimentResult, DominantLanguage, Entity, KeyPhrase, SyntaxToken}
import com.amazonaws.services.comprehend.{AmazonComprehendAsync, AmazonComprehendAsyncClientBuilder}
import scala.jdk.CollectionConverters._

object Comprehend {
  def apply: Comprehend = new Comprehend
}

class Comprehend {
  implicit val comprehend: Comprehend = this
  implicit val comprehendClient: AmazonComprehendAsync = AmazonComprehendAsyncClientBuilder.standard.build

  def detectDominantLanguages(text: String): List[DominantLanguage] = {
    import com.amazonaws.services.comprehend.model.{DetectDominantLanguageRequest, DetectDominantLanguageResult}

    val detectDominantLanguageRequest: DetectDominantLanguageRequest =
      new DetectDominantLanguageRequest().withText(text)
    val detectDominantLanguageResult: DetectDominantLanguageResult =
      comprehendClient.detectDominantLanguage(detectDominantLanguageRequest)
    detectDominantLanguageResult.getLanguages.asScala.toList
  }

  /** Returns the most frequently found dominant language */
  def detectDominantLanguage(text: String): DominantLanguage =
    detectDominantLanguages(text).groupBy(identity).maxBy(_._2.size)._1

  def detectEntities(text: String, languageCode: String="en"): List[Entity] = {
    import com.amazonaws.services.comprehend.model.DetectEntitiesRequest

    val detectEntitiesRequest = new DetectEntitiesRequest()
      .withText(text)
      .withLanguageCode(languageCode);
    val detectEntitiesResult  = comprehendClient.detectEntities(detectEntitiesRequest);
    detectEntitiesResult.getEntities.asScala.toList
  }

  def detectKeyPhrases(text: String, languageCode: String="en"): List[KeyPhrase] = {
    import com.amazonaws.services.comprehend.model.DetectKeyPhrasesRequest

    val detectKeyPhrasesRequest = new DetectKeyPhrasesRequest()
      .withText(text)
      .withLanguageCode(languageCode);
    val detectKeyPhrasesResult = comprehendClient.detectKeyPhrases(detectKeyPhrasesRequest);
    detectKeyPhrasesResult.getKeyPhrases.asScala.toList
  }

  def detectSentiment(text: String, languageCode: String="en"): DetectSentimentResult = {
    import com.amazonaws.services.comprehend.model.DetectSentimentRequest

    val detectSentimentRequest = new DetectSentimentRequest()
      .withText(text)
      .withLanguageCode(languageCode);
    val detectSentimentResult = comprehendClient.detectSentiment(detectSentimentRequest)
    detectSentimentResult
  }

  def detectSyntax(text: String, languageCode: String="en"): List[SyntaxToken] = {
    import com.amazonaws.services.comprehend.model.DetectSyntaxRequest

    val detectSyntaxRequest = new DetectSyntaxRequest()
      .withText(text)
      .withLanguageCode(languageCode);
    val detectSyntaxResult = comprehendClient.detectSyntax(detectSyntaxRequest);
    detectSyntaxResult.getSyntaxTokens.asScala.toList
  }
}

trait ComprehendImplicits {
}
