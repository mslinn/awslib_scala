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

import com.amazonaws.services.polly.{AmazonPollyAsync, AmazonPollyAsyncClientBuilder}

object Polly {
  def apply: Polly = new Polly
}

class Polly {
  implicit val polly: Polly = this
  implicit val pollyClient: AmazonPollyAsync = AmazonPollyAsyncClientBuilder.standard.build

  /** Obtain MP3 stream from AWS Polly that voices the message */
  def speechStream(message: String): java.io.InputStream = {
    import com.amazonaws.services.polly.model._

    val request = new SynthesizeSpeechRequest
    request.setVoiceId(VoiceId.Joanna)
    request.setOutputFormat(OutputFormat.Mp3)
    request.setText(message)
    val synthesizeSpeechResult: SynthesizeSpeechResult = pollyClient.synthesizeSpeech(request)
    synthesizeSpeechResult.getAudioStream
  }
}

trait PollyImplicits {
}
