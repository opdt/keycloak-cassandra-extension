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
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.*;

@Dao
public interface RealmDao {
  @Select(customWhereClause = "id = :id")
  Realm getRealmById(String id);

  @Select
  PagingIterable<Realm> findAll();

  @Insert
  void insert(Realm realm);

  @Delete
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

  // Attributes
  @Insert
  // Tabelle hat keine Non-PK-Columns -> Update nicht möglich, stattdessen Delete + Insert
  void insert(AttributeToRealmMapping mapping);

  @Update
  void insert(RealmToAttributeMapping mapping);

  @Select(customWhereClause = "realm_id = :realmId AND attribute_name = :attributeName")
  RealmToAttributeMapping findAttribute(String realmId, String attributeName);

  @Select(customWhereClause = "realm_id = :realmId")
  PagingIterable<RealmToAttributeMapping> findAllAttributes(String realmId);

  @Select(customWhereClause = "attribute_name = :attributeName AND attribute_value = :attributeValue")
  PagingIterable<AttributeToRealmMapping> findByAttribute(String attributeName, String attributeValue);

  @Delete
  boolean deleteAttributeToRealmMapping(AttributeToRealmMapping mapping);

  @Delete(entityClass = AttributeToRealmMapping.class)
  boolean deleteAttributeToRealmMapping(String attributeName, String attributeValue, String realmId);

  @Delete(entityClass = RealmToAttributeMapping.class)
  boolean deleteAllRealmToAttributeMappings(String realmId);

  @Delete(entityClass = RealmToAttributeMapping.class)
  boolean deleteAttribute(String realmId, String attributeName);
}
