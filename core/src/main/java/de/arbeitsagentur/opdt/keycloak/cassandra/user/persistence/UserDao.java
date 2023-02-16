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
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.*;

import java.util.List;

@Dao
public interface UserDao {
    @Insert
    void insert(User user);

    @Update
    ResultSet update(User user);

    @Update
    void update(FederatedIdentity federatedIdentity);

    @Update
    void update(FederatedIdentityToUserMapping identityToUserMapping);

    @Insert
    void insert(RealmToUserMapping realmToUserMapping);

    @Query("SELECT COUNT(id) FROM users")
    long count();

    @Select
    PagingIterable<User> findAll();

    @Select(customWhereClause = "realm_id = :realmId AND id = :id")
    User findById(String realmId, String id);

    @Select(customWhereClause = "realm_id = :realmId AND id IN :ids")
    PagingIterable<User> findByIds(String realmId, List<String> ids);

    @Select(customWhereClause = "user_id = :userId AND identity_provider = :identityProvider")
    FederatedIdentity findFederatedIdentity(String userId, String identityProvider);

    @Select(
        customWhereClause =
            "broker_user_id = :brokerUserId AND identity_provider = :identityProvider")
    FederatedIdentityToUserMapping findFederatedIdentityByBrokerUserId(
        String brokerUserId, String identityProvider);

    @Select(customWhereClause = "user_id = :userId")
    PagingIterable<FederatedIdentity> findFederatedIdentities(String userId);

    @Select(customWhereClause = "realm_id = :realmId")
    PagingIterable<RealmToUserMapping> findUsersByRealmId(String realmId);

    @Delete(ifExists = true)
    void delete(User User);

    @Delete
    boolean delete(FederatedIdentity federatedIdentity);

    @Delete
    boolean delete(FederatedIdentityToUserMapping identityToUserMapping);

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
    PagingIterable<UserSearchIndex> findUsers(String realmId, String name, String value);

    @Delete
    void delete(UserSearchIndex searchIndex);

    @Delete(entityClass = UserSearchIndex.class)
    void deleteIndex(String realmId, String name, String value, String userId);

    @Insert
    void insertOrUpdate(UserConsent userConsent);

    @Delete
    void delete(UserConsent userConsent);

    @Delete(entityClass = UserConsent.class)
    boolean deleteUserConsent(String realmId, String userId, String clientId);

    @Delete(entityClass = UserConsent.class,
        customWhereClause = "realm_id = :realmId AND user_id = :userId")
    boolean deleteUserConsentsByUserId(String realmId, String userId);

    @Select(customWhereClause = "realm_id = :realmId AND user_id = :userId AND client_id = :clientId")
    UserConsent findUserConsent(String realmId, String userId, String clientId);

    @Select(customWhereClause = "realm_id = :realmId AND user_id = :userId")
    PagingIterable<UserConsent> findUserConsentsByUserId(String realmId, String userId);

    @Select(customWhereClause = "realm_id = :realmId")
    PagingIterable<UserConsent> findUserConsentsByRealmId(String realmId);
}
