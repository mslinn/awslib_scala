/* Copyright 2012-2014 Micronautics Research Corporation.
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

import com.amazonaws.auth.policy.Statement.Effect
import com.amazonaws.auth.policy.actions.S3Actions
import com.amazonaws.auth.policy.{Principal, Resource, Statement}
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.{User => IAMUser, _}
import com.amazonaws.services.s3.model._
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object IAM {
  def apply(implicit awsCredentials: AWSCredentials): IAM = new IAM()

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
}

class IAM()(implicit val awsCredentials: AWSCredentials) {
  implicit lazy val iam = this
  implicit lazy val iamClient: AmazonIdentityManagementClient = new AmazonIdentityManagementClient

  /** (Re)creates an AWS IAM user with the given `userId`. Only PrivilegedUsers can have an associated IAM user.
    * Any pre-existing credentials are replaced.
    * @param maybeCredentials might contain new AWS IAM credentials */
  def createIAMUser(userName: String, maybeCredentials: Option[AWSCredentials]=None): Try[(IAMUser, AWSCredentials)] = {
    try {
      val oldIamUser: IAMUser = iamClient.getUser(new GetUserRequest().withUserName(userName)).getUser
      try { oldIamUser.deleteAccessKeys() } catch { case ignored: Exception => Logger.info(ignored.getMessage) }
      oldIamUser.createCredentials
      val credentials: AWSCredentials = maybeCredentials.getOrElse(oldIamUser.createCredentials)
      Success((oldIamUser, credentials))
    } catch {
      case e: NoSuchEntityException => // this is normal, if the IAM user does not exist create it
        try {
          Logger.info(e.getMessage)
          val createUserRequest = new CreateUserRequest().withUserName(userName)
          val iamUserNew: IAMUser = iamClient.createUser(createUserRequest).getUser
          val credentials = iamUserNew.createCredentials
          Logger.debug(s"New AWS IAM user $userName has path ${iamUserNew.getPath} and credentials $credentials")
          Success((iamUserNew, credentials))
        } catch {
          case e: Exception =>
            Failure(ExceptTrace(s"${e.getMessage}\nAWS IAM user $userName did not previously exist"))
        }

      case e: Exception =>
        Failure(e)
    }
  }

  def deleteAccessKeys(userName: String): Unit = {
    val listAccessKeysRequest = new ListAccessKeysRequest().withUserName(userName)
    iamClient.listAccessKeys(listAccessKeysRequest).getAccessKeyMetadata.asScala.foreach { accessKey =>
      val deleteAccessKeyRequest = new DeleteAccessKeyRequest().withUserName(userName).withAccessKeyId(accessKey.getAccessKeyId)
      iamClient.deleteAccessKey(deleteAccessKeyRequest)
    }
  }

  def deleteGroups(userName: String): Unit = try {
    iamClient.listGroupsForUser(new ListGroupsForUserRequest(userName)).getGroups.asScala.foreach { group =>
      iamClient.removeUserFromGroup(new RemoveUserFromGroupRequest().withUserName(userName).withGroupName(group.getGroupName))
    }
  }

  def deleteLoginProfile(userName: String): Unit = try {
    iamClient.deleteLoginProfile(new DeleteLoginProfileRequest(userName))
  } catch {
    case e: NoSuchEntityException => // no problem if this happens
      Logger.debug(e.getMessage)
  }

  def deleteUser(userName: String): Boolean = try {
    deleteAccessKeys(userName)
    deleteLoginProfile(userName)
    deleteGroups(userName)

    // The user must not belong to any groups, have any keys or signing certificates, or have any attached policies.
    val deleteUserRequest = new DeleteUserRequest().withUserName(userName)
    iamClient.deleteUser(deleteUserRequest)

    Logger.debug(s"Deleted AWS IAM user $userName")
    true
  } catch {
    case e: NoSuchEntityException => // no such user, non-fatal warning
      Logger.warn(e.getMessage)
      false

    case e: DeleteConflictException =>
      Logger.error(e.getMessage)
      false

    case e: Exception =>
      Logger.error(e.getMessage)
      false
  }

  def findUser(userName: String): Option[IAMUser] = try {
    Some(iamClient.getUser(new GetUserRequest().withUserName(userName)).getUser)
  } catch {
    case e: Exception =>
      Logger.warn(e.getMessage)
      None
  }

  def maybePrincipal(userName: String): Option[Principal] = findUser(userName).map { iamUser => new Principal(iamUser.getArn) }
}

trait IAMImplicits {
  implicit class RichIAMUser(val iamUser: IAMUser)(implicit iam: IAM) {
    def createCredentials: AWSCredentials = {
      import com.amazonaws.auth.BasicAWSCredentials
      import com.amazonaws.services.identitymanagement.model.CreateAccessKeyRequest

      val createAccessKeyRequest = new CreateAccessKeyRequest().withUserName(iamUser.getUserName)
      val accessKeyResult = iam.iamClient.createAccessKey(createAccessKeyRequest)
      new BasicAWSCredentials(accessKeyResult.getAccessKey.getAccessKeyId, accessKeyResult.getAccessKey.getSecretAccessKey)
    }

    def deleteAccessKeys(): Unit = iam.deleteAccessKeys(iamUser.getUserName)

    def deleteGroups(): Unit = iam.deleteGroups(iamUser.getUserName)

    def deleteLoginProfile(): Unit = iam.deleteLoginProfile(iamUser.getUserName)

    def deleteUser(): Boolean = iam.deleteUser(iamUser.getUserName)

    def principal: Principal = new Principal(iamUser.getArn)
  }
}
