/**
 *
 */

package com.micronautics.aws

import collection.JavaConverters._
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient
import com.amazonaws.services.elastictranscoder.model.{Pipeline, DeletePipelineRequest}
import scala.util.{Failure, Success, Try}

object ElasticTranscoder {
  import com.amazonaws.auth.AWSCredentials

  val defaultIamRole = "arn:aws:iam::031372724784:role/Elastic_Transcoder_Default_Role"
  val defaultIamRoleDetails =
    """{
      | "Version":"2008-10-17",
      | "Statement":[
      |   {
      |     "Sid":"1",
      |     "Effect":"Allow",
      |     "Action":["s3:ListBucket","s3:Put*","s3:Get*","s3:*MultipartUpload*"],
      |     "Resource":"*"},
      |   {
      |     "Sid":"2",
      |     "Effect":"Allow",
      |     "Action":"sns:Publish",
      |     "Resource":"*"
      |   },
      |   {
      |     "Sid":"3",
      |     "Effect":"Deny",
      |     "Action":["s3:*Policy*","sns:*Permission*","s3:*Acl*","sns:*Delete*","s3:*Delete*","sns:*Remove*"],
      |     "Resource":"*"
      |   }
      |  ]
      |}""".stripMargin

  def apply(implicit awsCredentials: AWSCredentials, cf: CloudFront, iam: IAM, s3: S3, sns: SNS): ElasticTranscoder = new ElasticTranscoder()
}

class ElasticTranscoder()(implicit val awsCredentials: AWSCredentials, cf: CloudFront, iam: IAM, s3: S3, sns: SNS) {
  import ElasticTranscoder._
  import com.amazonaws.services.elastictranscoder.model.{JobOutput, ListJobsByStatusRequest, Job, Preset}
  import com.amazonaws.services.s3.model.Bucket
  import util.Try

  implicit val etClient: AmazonElasticTranscoderClient = new AmazonElasticTranscoderClient
  implicit val et = this

  def allPresets: List[Preset] =
    try {
      import com.amazonaws.services.elastictranscoder.model.ListPresetsRequest
      etClient.listPresets(new ListPresetsRequest).getPresets.asScala.toList
    } catch {
      case e: Exception =>
        Logger.error(e.getMessage)
        Nil
    }

  // TODO rename this because it is no longer a cache
  def presetCache: Map[String, List[Preset]] = allPresets.groupBy(_.getId)

  /** Tries to cancel all jobs that match `outputName`, if specified. Errors are ignored. Only jobs with a status of `Submitted ` can be cancelled.
    * Race conditions are possible; jobs processed might start between the time they are listed and the time that cancellation is attempted.
    * This means that some jobs might not be cancelled.
    * This is a synchronous call. */
  def cancelJobs(pipeline: Pipeline, outputName: String=""): Unit = {
    import com.amazonaws.services.elastictranscoder.model.ListJobsByStatusRequest
    for {
      job <- etClient.listJobsByStatus(new ListJobsByStatusRequest().withStatus("Submitted")).getJobs.asScala if outputName=="" || job.getOutput.getKey==outputName
    } try {
      import com.amazonaws.services.elastictranscoder.model.CancelJobRequest
      etClient.cancelJob(new CancelJobRequest().withId(job.getId))
      ()
    } catch {
      case ignored: Exception =>
    }
  }

  /** Deletes any pre-existing output file before starting the Job. If this output file was being served directly,
    * in other words there is no CloudFront distribution for the output bucket, the output file will be unavailable until
    * the job completes. If the job fails, there is no output file.
    * TODO listen for job completion and if the output file existed previously, invalidate the output file so CloudFront can distribute the new version. */
  def createJob(bucket: Bucket, pipelineId: String, inputKey: String, outputKey: String, presetId: String): Try[Job] = {
    if (s3.listObjectsByPrefix(bucket.getName, inputKey, showSize=false).size==0) {
      Failure(ExceptTrace("Error: $inputKey does not exist in bucket ${bucket.getName}. Transcoding not attempted"))
    } else if (findJobByOutputKeyName(outputKey, pipelineId).isDefined) {
      Failure(ExceptTrace(s"Error: Job is still running with same output key ($outputKey)"))
    } else {
      try {
        import com.amazonaws.services.elastictranscoder.model.{CreateJobRequest, CreateJobOutput, JobInput}
        val files = s3.listObjectsByPrefix(bucket.getName, outputKey, showSize=false).toSeq
        files.foreach { file =>
          Logger.debug("Deleting ${bucket.getName}, $file")
          s3.deleteObject(bucket.getName, file)
        }
        val input = new JobInput().withKey(inputKey).withAspectRatio("auto").withContainer("auto").withFrameRate("auto").
          withInterlaced("auto").withResolution("auto")
        val output = new CreateJobOutput().withKey(outputKey).withPresetId(presetId).withRotate("auto").
          withThumbnailPattern(s"${outputKey}_{count}")
        val createJobRequest = new CreateJobRequest().withInput(input).withOutput(output).withPipelineId(pipelineId)
        val createJobResult = etClient.createJob(createJobRequest) // automatically queues for transcoding
        Success(createJobResult.getJob)
      } catch {
        case e: Exception =>
          Logger.warn(e.getMessage)
          Failure(e)
      }
    }
  }

  def createPipeline(name: String, inputBucket: Bucket, outputBucket: Bucket): Try[Pipeline] =
    try {
      import com.amazonaws.services.elastictranscoder.model.{CreatePipelineRequest, Notifications}

      val notifications = new Notifications().withCompleted("").withError("").withProgressing("").withWarning("")
      val createPipelineRequest = new CreatePipelineRequest().withName(name).withInputBucket(inputBucket.getName).
            withOutputBucket(outputBucket.getName).withRole(defaultIamRole).withNotifications(notifications)
      val pipeline = etClient.createPipeline(createPipelineRequest)
      Success(pipeline.getPipeline)
    } catch {
      case e: Exception => Failure(e)
    }

  def createPipelines(pipelineNames: List[String], inputBucket: Bucket, outputBucket: Bucket): List[Try[Pipeline]] =
    pipelineNames map { pipelineName =>
      (for {
        pipeline <- findPipelineByName(pipelineName)
      } yield Success(pipeline)).getOrElse {
        createPipeline(pipelineName, inputBucket, outputBucket)
      }
    }

   // TODO write a setter
   def defaultPresets: List[Preset] = try {
    List(presetCache("1351620000001-100070")).flatten
  } catch {
    case e: Exception =>
      Nil
  }

  def deletePipeline(pipeline: Pipeline): Unit = {
    etClient.deletePipeline(new DeletePipelineRequest().withId(pipeline.getId))
    ()
  }

  /** Deletes all pipelines associated with this AWS account. Errors are ignored.
    * When performing multiple deletions, a 300ms pause is inserted between requests. This is a synchronous call */
  def deletePipelines() = {
    val pipelines: List[Pipeline] = etClient.listPipelines.getPipelines.asScala.toList
    pipelines.foreach { pipeline =>
      try {
        cancelJobs(pipeline)
        pipeline.delete()
      } catch {
        case ignored: Exception =>
      }
    }
  }

  def dumpPipelines() = {
    val pipelines = etClient.listPipelines.getPipelines.asScala
    Logger.debug(s"${pipelines.size} pipelines: ")
    pipelines.foreach { pipeline =>
      val _jobs = jobs(pipeline)
      Logger.debug(s"  ${pipeline.getName} with ${_jobs.size} jobs: ${_jobs.map(_.getId).mkString(",")}")
    }
  }

  /** Find most recent job for specified key */
  // TODO Does not work because jobs are not returned in any chronological order
  def findJobByOutputKeyName(key: String, pipelineName: String): Option[Job] =
    findPipelineByName(pipelineName).flatMap { pipeline => // I hope jobs are returned with oldest first
      jobs(pipeline).find(_.getOutput.getKey == key)
    }

  /** Find most recent job for specified key */
  // TODO Does not work because jobs are not returned in any chronological order
  def findCompletedJobByInputKeyName(key: String, pipelineName: String): Option[Job] =
    findPipelineByName(pipelineName).flatMap { pipeline => // I hope jobs are returned with oldest first
      jobs(pipeline).find(j => j.getInput.getKey == key && j.getStatus == "Complete")
    }

  /** Find most recent job for specified key */
  // TODO Does not work because jobs are not returned in any chronological order
  def findJobByInputKeyName(key: String, pipelineName: String): Option[Job] =
    findPipelineByName(pipelineName).flatMap { pipeline => // I hope jobs are returned with oldest first
      jobs(pipeline).find(_.getInput.getKey == key)
    }

  /** Returns Some(pipeline) for the first pipeline found with the given
    * pipelineName associated this AWS account, or None if not found */
  def findPipelineById(pipelineId: String): Option[Pipeline] =
    etClient.listPipelines.getPipelines.asScala.find(_.getId == pipelineId)

  /** Pipeline names need not be unique. Returns Some(pipeline) for the first pipeline found with the given
    * pipelineName associated this AWS account, or None if not found */
  def findPipelineByName(pipelineName: String): Option[Pipeline] =
    etClient.listPipelines.getPipelines.asScala.find(_.getName == pipelineName)

  /** Pipeline names need not be unique. Returns List[Pipeline] containing all pipelines with the given
    * pipelineName associated this AWS account */
  def findPipelinesByName(pipelineName: String): List[Pipeline] =
    etClient.listPipelines.getPipelines.asScala.toList.filter(_.getName == pipelineName)

  def findPresetByName(name: String): Option[Preset] = {
    val mapByName: Map[String, Seq[Preset]] = allPresets.groupBy(_.getName)
    val candidatePresets: Iterable[Preset] = for {
      preset: Preset <- mapByName.getOrElse(name, Nil).headOption
    } yield preset
    candidatePresets.headOption
  }

  def findPresets(name: String): List[Preset] = {
    if (name=="all") defaultPresets
    else presetCache(name).toList
  }

  def isJobInProgress(pipeline: Pipeline, outputName: String): Boolean =
    etClient.listJobsByStatus(new ListJobsByStatusRequest().withStatus("Progressing")).getJobs.asScala.nonEmpty

  def jobs(pipeline: Pipeline): List[Job] = {
    import com.amazonaws.services.elastictranscoder.model.ListJobsByPipelineRequest
    etClient.listJobsByPipeline(new ListJobsByPipelineRequest().withPipelineId(pipeline.getId)).getJobs.asScala.toList
  }

  def jobStatuses(pipeline: Pipeline): List[JobOutput] = {
    import com.amazonaws.services.elastictranscoder.model.ListJobsByPipelineRequest
    val jobs: List[Job] = etClient.listJobsByPipeline(new ListJobsByPipelineRequest().withPipelineId(pipeline.getId)).getJobs.asScala.toList
    jobs.map(_.getOutput)
  }

  def jobOutputs(status: String): List[JobOutput] = {
    val jobs: List[Job] = etClient.listJobsByStatus(new ListJobsByStatusRequest().withStatus(status)).getJobs.asScala.toList
    jobs.map(_.getOutput)
  }

  /** Note that pipeline names need not be unique. Returns true if a pipeline is found for this AWS account with the
    * given pipelineName */
  def pipelineExists(pipelineName: String): Boolean =
    etClient.listPipelines.getPipelines.asScala.exists(_.getName==pipelineName)

  def presetsToHtml: Seq[String] = allPresets.map { preset =>
      s"""<div style="margin-bottom: 8pt"><b>Id:</b> ${preset.getId}<br/>
         |  <b>Name:</b> ${preset.getName}<br/>
         |  <b>Description:</b> ${preset.getDescription}<br/>
         |  <b>Container:</b> ${preset.getContainer}<br/>
         |  <b>Audio:</b> ${preset.getAudio}<br/>
         |  <b>Video:</b> ${preset.getVideo}<br/>
         |  <b>Thumbnails:</b> ${preset.getThumbnails}
         |</div>
         |""".stripMargin
    }.toSeq

  /** @return list of job output keys */
  // todo listen to job notifications, and add metadata that sets the last-modified-date to the date of the original file
  def transcode(pipelineId: String, inputKey: String, outputKey: String): List[Try[String]] =
    for {
      preset      <- defaultPresets
      pipeline    <- findPipelineById(pipelineId)
      inputBucket <- s3.findByName(pipeline.getInputBucket)
    } yield {
      if (s3.listObjectsByPrefix(pipeline.getInputBucket, inputKey, showSize=false).isEmpty) {
        Failure(ExceptTrace(s"Error: $inputKey does not exist in bucket ${pipeline.getInputBucket}. Transcoding not attempted"))
      } else {
        val objs = s3.listObjectsByPrefix(pipeline.getInputBucket, outputKey, showSize=false)
        objs foreach { outputKey =>
          Logger.debug(s"Deleting $outputKey from ${pipeline.getInputBucket}")
          s3.deleteObject(pipeline.getInputBucket, outputKey)
        }
        Logger.debug(s"About to transcode for pipeline $pipelineId, inputKey $inputKey, outputKey $outputKey, preset ${preset.getId}")
        createJob(inputBucket, pipelineId, inputKey, outputKey, preset.getId) map { job =>
          Logger.info(s"Transcoding $inputKey to $outputKey for pipeline #$pipelineId (${findPipelineById(pipelineId)}})")
          outputKey
        }
      }
    }

  def transcoderStatus(videoName: String, pipelineName: String): String = {
    val result = for {
      job <- findJobByOutputKeyName(videoName, pipelineName)
    } yield s"Video transcoder status: ${job.getOutput.getStatus} ${job.getOutput.getStatusDetail}"
    result.headOption.getOrElse("")
  }
}

trait ETImplicits {
  implicit class RichPipeline(pipeline: Pipeline)(implicit et: ElasticTranscoder) {
    def cancelJobs(outputName: String=""): Unit = et.cancelJobs(pipeline, outputName)

    def delete(): Unit = et.deletePipeline(pipeline)
  }
}
