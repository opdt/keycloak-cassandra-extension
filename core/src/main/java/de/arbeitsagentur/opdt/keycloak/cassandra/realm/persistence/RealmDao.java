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
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.ClientInitialAccess;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.NameToRealm;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.Realm;

@Dao
public interface RealmDao {
    @Select(customWhereClause = "id = :id")
    Realm getRealmById(String id);

    @Select
    PagingIterable<Realm> findAll();

    @Select(customWhereClause = "name = :name")
    NameToRealm findByName(String name);

    @Delete(entityClass = NameToRealm.class)
    void deleteNameToRealm(String name);

    @Insert(ifNotExists = true)
    void insert(Realm realm);

    @Update(customIfClause = "version = :expectedVersion")
    ResultSet update(Realm realm, long expectedVersion);

    @Update
    void insertOrUpdate(NameToRealm nameToRealm);

    @Delete(ifExists = true)
    void delete(Realm realm);

    // ClientInitialAccessModel

    @Select(customWhereClause = "realm_id = :realmId AND id = :id")
    ClientInitialAccess getClientInitialAccessModelById(String realmId, String id);

    @Select(customWhereClause = "realm_id = :realmId")
    PagingIterable<ClientInitialAccess> getClientInitialAccesses(String realmId);

    @Select
    PagingIterable<ClientInitialAccess> getAllClientInitialAccesses();

    @Update
    void insertOrUpdate(ClientInitialAccess model);

    @Update(ttl = ":ttl")
    void insertOrUpdate(ClientInitialAccess model, int ttl);

    @Delete(entityClass = ClientInitialAccess.class)
    void deleteClientInitialAccessModel(String realmId, String id);

    @Delete(entityClass = ClientInitialAccess.class)
    void deleteAllClientInitialAccessModels(String realmId);
}
