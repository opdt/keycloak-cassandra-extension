/*
 * Copyright 2022 IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.*;

@Dao
public interface UserDao {
  @Insert
  void insert(UserRealmRoleMapping userRealmRoleMapping);

  @Insert
  void insert(UserClientRoleMapping UserClientRoleMapping);

  @Insert
  void insert(User User);

  @Update
  void update(User User);

  @Update
  void update(FederatedIdentity federatedIdentity);

  @Update
  void update(FederatedIdentityToUserMapping identityToUserMapping);

  @Update
  void update(Credential credential);

  @Insert
    // Tabelle hat keine Non-PK-Columns -> Update nicht möglich, stattdessen Delete + Insert
  void insert(UserRequiredAction UserRequiredAction);

  @Insert
  void insert(RealmToUserMapping realmToUserMapping);

  @Query("SELECT COUNT(id) FROM users")
  long count();

  @Select
  PagingIterable<User> findAll();

  @Select(customWhereClause = "realm_id = :realmId AND id = :id")
  User findById(String realmId, String id);

  @Select(customWhereClause = "user_id = :userId")
  PagingIterable<UserRequiredAction> findAllRequiredActions(String userId);

  @Select(customWhereClause = "user_id = :userId AND identity_provider = :identityProvider")
  FederatedIdentity findFederatedIdentity(String userId, String identityProvider);

  @Select(
      customWhereClause =
          "broker_user_id = :brokerUserId AND identity_provider = :identityProvider")
  FederatedIdentityToUserMapping findFederatedIdentityByBrokerUserId(
      String brokerUserId, String identityProvider);

  @Select(customWhereClause = "user_id = :userId")
  PagingIterable<FederatedIdentity> findFederatedIdentities(String userId);

  @Select(customWhereClause = "user_id = :userId")
  PagingIterable<Credential> findCredentials(String userId);

  @Select(customWhereClause = "user_id = :userId AND id = :id")
  Credential findCredential(String userId, String id);

  @Select(customWhereClause = "realm_id = :realmId")
  PagingIterable<RealmToUserMapping> findUsersByRealmId(String realmId);

  @Select(customWhereClause = "user_id = :userId")
  PagingIterable<UserRealmRoleMapping> findRealmRolesByUserId(String userId);

  @Select(customWhereClause = "user_id = :userId AND client_id = :clientId")
  PagingIterable<UserClientRoleMapping> findAllByUserIdAndClientId(
      String userId, String clientId);

  @Select(customWhereClause = "user_id = :userId")
  PagingIterable<UserClientRoleMapping> findAllClientRoleMappingsByUserId(String userId);

  @Delete
  boolean removeRoleMapping(UserRealmRoleMapping userRealmRoleMapping);

  @Delete
  boolean removeClientRoleMapping(UserClientRoleMapping UserClientRoleMapping);

  @Delete
  void delete(User User);

  @Delete(entityClass = UserRequiredAction.class)
  void deleteAllRequiredActions(String userId);

  @Delete
  boolean delete(FederatedIdentity federatedIdentity);

  @Delete
  boolean delete(FederatedIdentityToUserMapping identityToUserMapping);

  @Delete
  boolean delete(Credential credential);

  @Delete(entityClass = Credential.class)
  boolean deleteAllCredentials(String userId);

  @Delete
  boolean delete(UserRequiredAction UserRequiredAction);

  @Delete(entityClass = UserRequiredAction.class)
  boolean deleteRequiredAction(String userId, String requiredAction);

  @Delete(entityClass = RealmToUserMapping.class)
  boolean deleteRealmToUserMapping(String realmId, boolean serviceAccount, String userId);

  @Query("SELECT count(user_id) FROM realms_to_users WHERE realm_id = :realmId")
  long countAllUsersByRealmId(String realmId);

  @Query(
      "SELECT count(user_id) FROM realms_to_users WHERE realm_id = :realmId AND service_account = false")
  long countNonServiceAccountUsersByRealmId(String realmId);

  // Search
  @Insert
  void insertOrUpdate(UserSearchIndex searchIndex);

  @Select(customWhereClause = "realm_id = :realmId AND name = :name AND value = :value")
  UserSearchIndex findUser(String realmId, String name, String value);

  @Delete
  void delete(UserSearchIndex searchIndex);

  @Delete(entityClass = UserSearchIndex.class)
  void deleteIndex(String realmId, String name, String value);
}
