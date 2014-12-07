/**
 *
 */

package com.micronautics.aws

import AwsCredentials._
import collection.JavaConverters._
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient

object ElasticTranscoder {
  import com.amazonaws.auth.AWSCredentials

  def apply(implicit awsCredentials: AWSCredentials, etClient: AmazonElasticTranscoderClient=new AmazonElasticTranscoderClient): ElasticTranscoder =
    new ElasticTranscoder()(awsCredentials, etClient)
}

class ElasticTranscoder()(implicit val awsCredentials: AWSCredentials, val snsClient: AmazonElasticTranscoderClient=new AmazonElasticTranscoderClient) {
}

trait ETImplicits {

}

