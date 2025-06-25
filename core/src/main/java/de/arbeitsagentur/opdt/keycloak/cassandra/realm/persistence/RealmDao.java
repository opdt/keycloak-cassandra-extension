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
package de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.ClientInitialAccess;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.NameToRealm;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.Realm;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalDao;

@Dao
public interface RealmDao extends TransactionalDao<Realm> {
    @Select(customWhereClause = "id = :id")
    @StatementAttributes(executionProfileName = "read")
    Realm getRealmById(String id);

    @Select
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<Realm> findAll();

    @Select(customWhereClause = "name = :name")
    @StatementAttributes(executionProfileName = "read")
    NameToRealm findByName(String name);

    @Delete(entityClass = NameToRealm.class)
    @StatementAttributes(executionProfileName = "write")
    void deleteNameToRealm(String name);

    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(NameToRealm nameToRealm);

    // ClientInitialAccessModel

    @Select(customWhereClause = "realm_id = :realmId AND id = :id")
    @StatementAttributes(executionProfileName = "read")
    ClientInitialAccess getClientInitialAccessModelById(String realmId, String id);

    @Select(customWhereClause = "realm_id = :realmId")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<ClientInitialAccess> getClientInitialAccesses(String realmId);

    @Select
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<ClientInitialAccess> getAllClientInitialAccesses();

    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(ClientInitialAccess model);

    @Update(ttl = ":ttl")
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(ClientInitialAccess model, int ttl);

    @Delete(entityClass = ClientInitialAccess.class)
    @StatementAttributes(executionProfileName = "write")
    void deleteClientInitialAccessModel(String realmId, String id);

    @Delete(entityClass = ClientInitialAccess.class)
    @StatementAttributes(executionProfileName = "write")
    void deleteAllClientInitialAccessModels(String realmId);
}
