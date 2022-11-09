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

import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.RoleToAttributeMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.ClientRole;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.RealmRole;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.Role;

import java.util.List;
import java.util.stream.Stream;

public interface RoleRepository {
  void addOrUpdateRole(Role role);

  boolean removeRealmRole(String realmId, String roleId);

  boolean removeClientRole(String clientId, String roleId);

  void removeAllRealmRoles(String realmId);

  void removeAllClientRoles(String clientId);

  RealmRole getRealmRoleByName(String realmId, String name);

  ClientRole getClientRoleByName(String clientId, String name);

  Stream<RealmRole> getAllRealmRoles(String realmId, Integer firstResult, Integer maxResult);

  Stream<ClientRole> getAllClientRoles(String clientId, Integer firstResult, Integer maxResult);

  void updateAttribute(RoleToAttributeMapping attributeMapping);

  void deleteAttribute(String roleId, String attributeName);

  RoleToAttributeMapping findRoleAttribute(String roleId, String attributeName);

  List<RoleToAttributeMapping> findAllRoleAttributes(String roleId);

  Role getRoleById(String id);

  Stream<Role> getRolesByIds(List<String> ids, Integer firstResult, Integer maxResult);
}
