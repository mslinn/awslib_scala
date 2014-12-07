package com.micronautics.aws

trait Init {
  import com.amazonaws.auth.AWSCredentials

  lazy implicit val awsCredentials: AWSCredentials = maybeCredentialsFromEnv.getOrElse(
                                                       maybeCredentialsFromFile.getOrElse(
                                                         sys.error("No AWS credentials found in environment variables and no .s3 file was found in the working directory, or a parent directory.")))
  lazy implicit val s3: S3 = S3(awsCredentials)
  lazy implicit val cf: CloudFront = CloudFront(awsCredentials)
  lazy implicit val iam: IAM = IAM(awsCredentials)
  lazy implicit val sns: SNS = SNS(awsCredentials)
}
