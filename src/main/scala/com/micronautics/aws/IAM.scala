package com.micronautics.aws

import collection.JavaConverters._
import com.amazonaws.auth.{BasicAWSCredentials, AWSCredentials}
import com.amazonaws.auth.policy.Statement.Effect
import com.amazonaws.auth.policy.actions.S3Actions
import com.amazonaws.auth.policy.{Principal, Resource, Statement}
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.{User => IAMUser, _}
import com.amazonaws.services.s3.model._
import org.slf4j.LoggerFactory
import util.{Failure, Success, Try}

object IAM {
  lazy val Logger = LoggerFactory.getLogger("IAM")

  def allowAllStatement(bucket: Bucket, principals: Seq[Principal], idString: String): Statement =
    allowSomeStatement(bucket, principals, List(S3Actions.AllS3Actions), idString)

  def allowSomeStatement(bucket: Bucket, principals: Seq[Principal], actions: Seq[S3Actions], idString: String): Statement =
    allowSomeStatement(List(new Resource("arn:aws:s3:::" + bucket.getName)), principals, actions, idString)

  def allowSomeStatement(resources: Seq[Resource], principals: Seq[Principal], actions: Seq[S3Actions], idString: String): Statement =
    new Statement(Effect.Allow)
      .withId(idString)
      .withActions(actions: _*)
      .withPrincipals(principals: _*)
      .withResources(resources: _*)

  implicit class RichBucket(bucket: Bucket) {
    def allowAllStatement(principals: Seq[Principal], idString: String): Statement =
      IAM.allowAllStatement(bucket, principals, idString)

    def allowSomeStatement(principals: Seq[Principal], actions: Seq[S3Actions], idString: String): Statement =
      IAM.allowSomeStatement(bucket, principals, actions, idString)
  }

  implicit class RichIAMUser(iamUser: IAMUser)(implicit iamClient: AmazonIdentityManagementClient) {
    def createCredentials: AWSCredentials = {
      val createAccessKeyRequest = new CreateAccessKeyRequest().withUserName(iamUser.getUserId)
      val accessKeyResult = iamClient.createAccessKey(createAccessKeyRequest)
      new BasicAWSCredentials(accessKeyResult.getAccessKey.getAccessKeyId, accessKeyResult.getAccessKey.getSecretAccessKey)
    }
  }
}

case class IAM(implicit iamClient: AmazonIdentityManagementClient) {
  import com.micronautics.aws.IAM._

  /** (Re)creates an AWS IAM user with the given `userId`
    * @param maybeCredentials might contain new AWS IAM credentials */
  def createIAMUser(userId: String, maybeCredentials: Option[AWSCredentials]=None): Try[(IAMUser, AWSCredentials)] = {
    try {
      val oldIamUser = iamClient.getUser(new GetUserRequest().withUserName(userId)).getUser
      val credentials = maybeCredentials.getOrElse(oldIamUser.createCredentials)
      Success((oldIamUser, credentials))
    } catch {
      case e: NoSuchEntityException =>
        try {
          val createUserRequest = new CreateUserRequest().withUserName(userId)
          val iamUserNew: IAMUser = iamClient.createUser(createUserRequest).getUser
          val credentials = iamUserNew.createCredentials
          Logger.debug(s"New AWS IAM user $userId has path ${iamUserNew.getPath} and credentials $credentials")
          Success((iamUserNew, credentials))
        } catch {
          case e: Exception =>
            Failure(new Exception(s"${e.getMessage}\nAWS IAM user $userId did not previously exist"))
        }

      case e: Exception =>
        Failure(new Exception(e.getMessage))
    }
  }

  def deleteGroups(userId: String): Unit = try {
    iamClient.listGroupsForUser(new ListGroupsForUserRequest(userId)).getGroups.asScala.foreach { group =>
      iamClient.removeUserFromGroup(new RemoveUserFromGroupRequest().withUserName(userId).withGroupName(group.getGroupName))
    }
  }

  def deleteIAMUser(userId: String): Unit = try {
     val deleteAccessKeyRequest = new DeleteAccessKeyRequest().withUserName(userId)
     iamClient.deleteAccessKey(deleteAccessKeyRequest)

     val deleteLoginProfileRequest = new DeleteLoginProfileRequest().withUserName(userId)
     iamClient.deleteLoginProfile(deleteLoginProfileRequest)

     val deleteUserRequest = new DeleteUserRequest().withUserName(userId)
     iamClient.deleteUser(deleteUserRequest)

     Logger.debug(s"Deleted AWS IAM user $userId")
   } catch { case e: Throwable => }

  def deleteLoginProfile(userId: String): Unit = try {
    iamClient.deleteLoginProfile(new DeleteLoginProfileRequest(userId))
  } catch {
    case e: NoSuchEntityException => // no problem if this happens
      Logger.debug(e.getMessage)
  }

  def deleteAccessKeys(userId: String): Unit = {
    val listAccessKeysRequest = new ListAccessKeysRequest().withUserName(userId)
    iamClient.listAccessKeys(listAccessKeysRequest).getAccessKeyMetadata.asScala.foreach { accessKey =>
      val deleteAccessKeyRequest = new DeleteAccessKeyRequest().withUserName(userId).withAccessKeyId(accessKey.getAccessKeyId)
      iamClient.deleteAccessKey(deleteAccessKeyRequest)
    }
  }

  def deleteUser(userId: String): Boolean = try {
    deleteAccessKeys(userId)
    deleteLoginProfile(userId)
    deleteGroups(userId)

    // The user must not belong to any groups, have any keys or signing certificates, or have any attached policies.
    val deleteUserRequest = new DeleteUserRequest().withUserName(userId)
    iamClient.deleteUser(deleteUserRequest)

    Logger.debug(s"Deleted AWS IAM user $userId")
    true
  } catch {
    case e: NoSuchEntityException => // no such user, non-fatal warning
      Logger.warn(e.getMessage)
      false

    case e: DeleteConflictException => // Request rejected because it attempted to delete a resource with attached subordinate entities.
      Logger.error(e.getMessage)
      false

    case e: Exception =>
      Logger.error(e.getMessage)
      false
  }
}
