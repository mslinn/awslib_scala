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

import com.amazonaws.auth.policy.Statement.Effect
import com.amazonaws.auth.policy.actions.S3Actions
import com.amazonaws.auth.policy.{Principal, Resource, Statement}
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.identitymanagement.{AmazonIdentityManagement, AmazonIdentityManagementClientBuilder}
import com.amazonaws.services.identitymanagement.model.{User => IAMUser, _}
import com.amazonaws.services.s3.model._
import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

object IAM {
  def apply: IAM = new IAM

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

class IAM {
  implicit val iam: IAM = this
  implicit val iamClient: AmazonIdentityManagement = AmazonIdentityManagementClientBuilder.standard.build

  /** (Re)creates an AWS IAM user with the given `userId`. Only PrivilegedUsers can have an associated IAM user.
    * Any pre-existing credentials are replaced. */
  def createIAMUser(userName: String): Try[(IAMUser, AWSCredentials)] = {
    try {
      val oldIamUser: IAMUser = iamClient.getUser(new GetUserRequest().withUserName(userName)).getUser
      try { oldIamUser.deleteAccessKeys() } catch { case ignored: Exception => Logger.info(ignored.getMessage) }
      oldIamUser.createAccessKeys().map { keys =>
        (oldIamUser, keys)
      }
    } catch {
      case e: NoSuchEntityException => // this is normal, if the IAM user does not exist create one
        try {
          Logger.info(e.getMessage)
          val createUserRequest = new CreateUserRequest().withUserName(userName)
          val iamUserNew: IAMUser = iamClient.createUser(createUserRequest).getUser
          iamUserNew.createAccessKeys().map { keys =>
            Logger.debug(s"New AWS IAM user $userName has path ${iamUserNew.getPath} and keys $keys")
            (iamUserNew, keys)
          }
        } catch {
          case e: Exception =>
            Failure(ExceptTrace(s"${e.getMessage}\nAWS IAM user $userName did not previously exist"))
        }

      case e: Exception =>
        Failure(e)
    }
  }

  /** @return Try[Boolean] indicating that no problems were encountered, or there were no keys to delete */
  def deleteAccessKeys(userName: String): Try[Boolean] = Try {
    val listAccessKeysRequest = new ListAccessKeysRequest().withUserName(userName)
    iamClient.listAccessKeys(listAccessKeysRequest).getAccessKeyMetadata.asScala.map { accessKey =>
      val deleteAccessKeyRequest = new DeleteAccessKeyRequest().withUserName(userName).withAccessKeyId(accessKey.getAccessKeyId)
      iamClient.deleteAccessKey(deleteAccessKeyRequest)
      true
    }.forall(_==true)
  }

  /** @return Try[Boolean] indicating that no problems were encountered, or the user had no groups to delete */
  def deleteGroups(userName: String): Try[Boolean] = Try {
    iamClient.listGroupsForUser(new ListGroupsForUserRequest(userName)).getGroups.asScala.map { group =>
      iamClient.removeUserFromGroup(new RemoveUserFromGroupRequest().withUserName(userName).withGroupName(group.getGroupName))
      true
    }.forall(_==true)
  }

  def deleteLoginProfile(userName: String): Try[Boolean] = Try {
    iamClient.deleteLoginProfile(new DeleteLoginProfileRequest(userName))
    true
  }

  def deleteUser(userName: String): Try[Boolean] = Try {
    deleteAccessKeys(userName)
    deleteLoginProfile(userName)
    deleteGroups(userName)

    // The user must not belong to any groups, have any keys or signing certificates, or have any attached policies.
    val deleteUserRequest = new DeleteUserRequest().withUserName(userName)
    iamClient.deleteUser(deleteUserRequest)

    Logger.debug(s"Deleted AWS IAM user $userName")
    true
  }

  def findUser(userName: String): Try[IAMUser] = Try {
    iamClient.getUser(new GetUserRequest().withUserName(userName)).getUser
  }

  def maybePrincipal(userName: String): Try[Principal] =
    findUser(userName).map { iamUser => new Principal(iamUser.getArn) }
}

trait IAMImplicits {
  implicit class RichIAMUser(val iamUser: IAMUser)(implicit iam: IAM) {
    /** An IAM user can only have two sets of credentials. If a request is made create more, the returned Try will
      * contain the com.amazonaws.services.identitymanagement.model.LimitExceededException.
      * If this request succeeds, the new keys are associated with the IAMUser */
    def createAccessKeys(): Try[AWSCredentials] = Try {
      import com.amazonaws.auth.BasicAWSCredentials
      import com.amazonaws.services.identitymanagement.model.CreateAccessKeyRequest

      val createAccessKeyRequest = new CreateAccessKeyRequest().withUserName(iamUser.getUserName)
      val accessKey: AccessKey = iam.iamClient.createAccessKey(createAccessKeyRequest).getAccessKey
      new BasicAWSCredentials(accessKey.getAccessKeyId, accessKey.getSecretAccessKey)
    }

    def deleteAccessKeys():  Try[Boolean] = iam.deleteAccessKeys(iamUser.getUserName)

    def deleteGroups():  Try[Boolean] = iam.deleteGroups(iamUser.getUserName)

    def deleteLoginProfile(): Try[Boolean] = iam.deleteLoginProfile(iamUser.getUserName)

    def deleteUser(): Try[Boolean] = iam.deleteUser(iamUser.getUserName)

    def principal: Principal = new Principal(iamUser.getArn) // specify account id instead of user arn?!?!
  }
}
