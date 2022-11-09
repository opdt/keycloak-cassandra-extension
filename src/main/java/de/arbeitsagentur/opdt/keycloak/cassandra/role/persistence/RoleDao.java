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
package de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.*;

import java.util.List;

@Dao
public interface RoleDao {
  @Update
  void insertOrUpdate(RealmRole realmRole);

  @Update
  void insertOrUpdate(ClientRole clientRole);

  @Update
  void insertOrUpdate(Role role);

  @Insert // Tabelle hat keine Non-PK-Columns -> Update nicht möglich, stattdessen Delete + Insert
  void insert(AttributeToRoleMapping mapping);

  @Update
  void insertOrUpdate(RoleToAttributeMapping mapping);

  @Select(customWhereClause = "realm_id = :realmId")
  PagingIterable<RealmRole> findAllRealmRoles(String realmId);

  @Select(customWhereClause = "client_id = :clientId")
  PagingIterable<ClientRole> findAllClientRoles(String clientId);

  @Select(customWhereClause = "id = :id")
  Role getRoleById(String id);

  @Select(customWhereClause = "id IN :ids")
  PagingIterable<Role> getRolesByIds(List<String> ids);

  @Select(customWhereClause = "realm_id = :realmId AND name = :name")
  RealmRole getRealmRoleByName(String realmId, String name);

  @Select(customWhereClause = "client_id = :clientId AND name = :name")
  ClientRole getClientRoleByName(String clientId, String name);

  @Select(customWhereClause = "role_id = :roleId AND attribute_name = :attributeName")
  RoleToAttributeMapping findAttribute(String roleId, String attributeName);

  @Select(customWhereClause = "role_id = :roleId")
  PagingIterable<RoleToAttributeMapping> findAllAttributes(String roleId);

  @Delete(entityClass = RealmRole.class)
  void deleteAllRealmRoles(String realmId);

  @Delete(entityClass = ClientRole.class)
  void deleteAllClientRoles(String clientId);

  @Delete(entityClass = Role.class)
  void deleteRole(String id);

  @Delete(entityClass = RealmRole.class)
  boolean deleteRealmRole(String realmId, String name);

  @Delete(entityClass = ClientRole.class)
  boolean deleteClientRole(String clientId, String name);

  @Delete
  boolean deleteAttributeToRoleMapping(AttributeToRoleMapping attributeToRoleMapping);

  @Delete(entityClass = RoleToAttributeMapping.class)
  boolean deleteAttribute(String roleId, String attributeName);
}
