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

import com.amazonaws.services.polly.model._
import com.amazonaws.services.polly.{AmazonPollyAsync, AmazonPollyAsyncClientBuilder}
import scala.jdk.CollectionConverters._

object Polly {
  def apply: Polly = new Polly
}

class Polly {
  import com.amazonaws.services.polly.model.{LanguageCode, OutputFormat, SpeechMarkType, TextType, VoiceId}

  implicit val polly: Polly = this
  implicit val pollyClient: AmazonPollyAsync = AmazonPollyAsyncClientBuilder.standard.build

  def describeVoices(languageCode: String= "en-US"): List[Voice] = {
    import com.amazonaws.services.polly.model.{DescribeVoicesRequest, DescribeVoicesResult}

    @scala.annotation.tailrec
    def getEmAll(request: DescribeVoicesRequest, accum: List[Voice]=Nil): List[Voice] = {
      val describeVoicesResult: DescribeVoicesResult = pollyClient.describeVoices(request)
      val voices: List[Voice] = describeVoicesResult.getVoices.asScala.toList
      val updatedAccum: List[Voice] = voices ::: accum
      val nextToken: String = describeVoicesResult.getNextToken

      if (nextToken!=null) {
        request.setNextToken(nextToken)
        getEmAll(request, updatedAccum)
      } else
        updatedAccum
    }

    val request = new DescribeVoicesRequest().withLanguageCode(languageCode)
    val result: List[Voice] = getEmAll(request, Nil)
    result
  }

  /** See https://docs.aws.amazon.com/polly/latest/dg/managing-lexicons.html */
  def lexiconFor(name: String): Either[Exception, Lexicon] = {
    import com.amazonaws.services.polly.model.GetLexiconRequest
    val getLexiconRequest = new GetLexiconRequest().withName(name)

    try {
      val getLexiconResult = pollyClient.getLexicon(getLexiconRequest)
      Right(getLexiconResult.getLexicon)
    } catch {
      case e: Exception =>
        Left(e)
    }
  }

  /** Store a pronunciation lexicon in the AWS region the the code is executing under */
  def putLexicon(name: String, content: String): Either[Exception, PutLexiconResult] = {
    import com.amazonaws.services.polly.model.PutLexiconRequest

    val putLexiconRequest = new PutLexiconRequest()
            .withContent(content)
            .withName(name)
    try {
      Right(pollyClient.putLexicon(putLexiconRequest))
    } catch {
      case e: Exception =>
        Left(e)
    }
  }

  /** @return List of Lexicons supported by the AWS region that the code is running against. */
  def regionalLexicons: List[LexiconDescription] = {
    import com.amazonaws.services.polly.model.{LexiconDescription, ListLexiconsRequest, ListLexiconsResult}

    @scala.annotation.tailrec
    def getEmAll(request: ListLexiconsRequest, accum: List[LexiconDescription]=Nil): List[LexiconDescription] = {
      val listLexiconsResult: ListLexiconsResult = pollyClient.listLexicons(request)
      val lexicons: List[LexiconDescription] = listLexiconsResult.getLexicons.asScala.toList
      val updatedAccum: List[LexiconDescription] = lexicons ::: accum
      val nextToken: String = listLexiconsResult.getNextToken

      if (nextToken!=null) {
        request.setNextToken(nextToken)
        getEmAll(request, updatedAccum)
      } else
        updatedAccum
    }

    val request = new ListLexiconsRequest()
    val result: List[LexiconDescription] = getEmAll(request, Nil)
    result
  }

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

  /** Asynchronous; starts task and returns immediately */
  def startSpeechSynthesisTask(text: String,
                               languageCode: LanguageCode = LanguageCode.EnUS,
                               outputBucket: RichBucket,
                               snsTopicArn: Arn,
                               engine: String = "neural",
                               lexiconNames: Seq[String] = Seq.empty,
                               outputFormat: OutputFormat = OutputFormat.Mp3,
                               s3KeyPrefix: String = "",
                               sampleRate: Option[String] = None,
                               speechMarkTypes: Seq[SpeechMarkType] = Nil,
                               textType: TextType = TextType.Text,
                               voiceId: VoiceId = VoiceId.Amy
  ): StartSpeechSynthesisTaskResult = {
    import com.amazonaws.services.polly.model._

    val request: StartSpeechSynthesisTaskRequest = {
      val r = new StartSpeechSynthesisTaskRequest()
      .withEngine(engine)
      .withLanguageCode(languageCode)
      .withLexiconNames(lexiconNames.asJava)
      .withOutputFormat(outputFormat.toString)
      .withOutputS3BucketName(outputBucket.bucket.getName)
      .withOutputS3KeyPrefix(s3KeyPrefix)
      .withSnsTopicArn(snsTopicArn.arnString)
      .withText(text)
      .withTextType(textType)
      .withVoiceId(voiceId)

      val s: StartSpeechSynthesisTaskRequest = sampleRate.map(r.withSampleRate) getOrElse r
      val t: StartSpeechSynthesisTaskRequest = if (speechMarkTypes.nonEmpty) s.withSpeechMarkTypes(speechMarkTypes: _*) else s
      t
    }

    pollyClient.startSpeechSynthesisTask(request)
  }

  /** Blocks until completion.
    * See https://docs.aws.amazon.com/polly/latest/dg/StartSpeechSynthesisTask.html
    * Neural voices: https://docs.aws.amazon.com/polly/latest/dg/ntts-voices-main.html#ntts-voices-main */
  def startSpeechSynthesisTaskBlocking(text: String,
                                       outputBucket: RichBucket,
                                       snsTopicArn: Arn,
                                       engine: String = "neural",
                                       languageCode: LanguageCode = LanguageCode.EnUS,
                                       lexiconNames: Seq[String] = Seq.empty,
                                       outputFormat: OutputFormat = OutputFormat.Mp3,
                                       s3KeyPrefix: String = "",
                                       sampleRate: Option[String] = None,
                                       speechMarkTypes: Seq[SpeechMarkType] = Nil,
                                       textType: TextType = TextType.Text,
                                       voiceId: VoiceId = VoiceId.Joanna
                                      ): Unit = {
    import java.time.Duration
    import java.util.concurrent.TimeUnit
    import com.amazonaws.services.polly.model.TaskStatus
    import org.awaitility.Awaitility.await
    import org.awaitility.Durations

    def getSynthesisTaskStatus(taskId: String): String = {
      import com.amazonaws.services.polly.model.GetSpeechSynthesisTaskRequest

      val getSpeechSynthesisTaskRequest = new GetSpeechSynthesisTaskRequest()
                .withTaskId(taskId)
        val result = pollyClient.getSpeechSynthesisTask(getSpeechSynthesisTaskRequest)
        result.getSynthesisTask.getTaskStatus
    }

    val SYNTHESIS_TASK_TIMEOUT_SECONDS: Long = 300L
    val SYNTHESIS_TASK_POLL_INTERVAL = Durations.FIVE_SECONDS
    val SYNTHESIS_TASK_POLL_DELAY: Duration = Durations.TEN_SECONDS

    val result: StartSpeechSynthesisTaskResult = startSpeechSynthesisTask(
      text = text,
      outputBucket = outputBucket,
      snsTopicArn = snsTopicArn,
      languageCode = languageCode,
      lexiconNames = lexiconNames,
      outputFormat = outputFormat,
      s3KeyPrefix = s3KeyPrefix,
      sampleRate = sampleRate,
      speechMarkTypes = speechMarkTypes,
      textType = textType,
      voiceId = voiceId
    )
    val taskId = result.getSynthesisTask.getTaskId
    await
      .`with`
      .pollInterval(SYNTHESIS_TASK_POLL_INTERVAL)
      .pollDelay(SYNTHESIS_TASK_POLL_DELAY)
      .atMost(SYNTHESIS_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .until(() => getSynthesisTaskStatus(taskId).equals(TaskStatus.Completed.toString))
    ()
  }

  /** See https://docs.aws.amazon.com/polly/latest/dg/speechmarks.html */
  def synthesizeSpeechMarks(text: String,
                            outputFileName: String,
                            engine: String = "neural",
                            languageCode: LanguageCode = LanguageCode.EnUS,
                            outputFormat: OutputFormat = OutputFormat.Json,
                            speechMarkTypes: Seq[SpeechMarkType] = List(SpeechMarkType.Viseme, SpeechMarkType.Word),
                            textType: TextType = TextType.Text,
                            voiceId: VoiceId = VoiceId.Joanna
                           ): Either[Exception, Array[Byte]] = {
    import com.amazonaws.services.polly.model.{SynthesizeSpeechRequest, SynthesizeSpeechResult}
    import org.apache.commons.io.IOUtils

    val synthesizeSpeechRequest = new SynthesizeSpeechRequest()
      .withEngine(engine)
      .withLanguageCode(languageCode)
      .withOutputFormat(outputFormat)
      .withSpeechMarkTypes(speechMarkTypes: _*)
      .withText(text)
      .withTextType(textType)
      .withVoiceId(voiceId)

    try {
      val synthesizeSpeechResult: SynthesizeSpeechResult = pollyClient.synthesizeSpeech(synthesizeSpeechRequest)
      val audioBytes: Array[Byte] = IOUtils.toByteArray(synthesizeSpeechResult.getAudioStream)
      Right(audioBytes)
    } catch {
      case e: Exception =>
        Left(e)
    }
  }
}

trait PollyImplicits {
}
