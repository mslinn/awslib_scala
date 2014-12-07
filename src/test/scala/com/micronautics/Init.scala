package com.micronautics.aws

trait Init {
  import com.amazonaws.auth.AWSCredentials

  lazy implicit val awsCredentials: AWSCredentials = maybeCredentialsFromEnv.getOrElse(
                                                       maybeCredentialsFromFile.getOrElse(
                                                         sys.error("No AWS credentials found in environment variables and no .s3 file was found in the working directory, or a parent directory.")))
  lazy implicit val et: ElasticTranscoder = new ElasticTranscoder()
  lazy implicit val cf: CloudFront = new CloudFront()
  lazy implicit val iam: IAM = new IAM()
  lazy implicit val s3: S3 = new S3()
  lazy implicit val sns: SNS = new SNS()
}
