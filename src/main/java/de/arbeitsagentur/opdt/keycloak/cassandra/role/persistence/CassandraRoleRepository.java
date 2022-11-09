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

import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Stream;

import static de.arbeitsagentur.opdt.keycloak.cassandra.StreamExtensions.paginated;


@RequiredArgsConstructor
public class CassandraRoleRepository implements RoleRepository {
  private final RoleDao roleDao;

  @Override
  public void addOrUpdateRole(Role role) {
    roleDao.insertOrUpdate(role);

    if (role.isClientRole()) {
      roleDao.insertOrUpdate(
          new ClientRole(
              role.getClientId(),
              role.getName(),
              role.getId(),
              role.getDescription(),
              role.getRealmId(),
              role.getChildRoles()));
    } else {
      roleDao.insertOrUpdate(
          new RealmRole(
              role.getRealmId(),
              role.getName(),
              role.getId(),
              role.getDescription(),
              role.getChildRoles()));
    }
  }

  @Override
  public boolean removeRealmRole(String realmId, String roleId) {
    Role role = roleDao.getRoleById(roleId);
    if(role == null) {
      return false;
    }

    roleDao.deleteRole(roleId);
    roleDao.deleteRealmRole(realmId, role.getName());
    return true;
  }

  @Override
  public boolean removeClientRole(String clientId, String roleId) {
    Role role = roleDao.getRoleById(roleId);

    if(role == null) {
      return false;
    }
    roleDao.deleteRole(roleId);
    roleDao.deleteClientRole(clientId, role.getName());
    return true;
  }

  @Override
  public void removeAllRealmRoles(String realmId) {
    getAllRealmRoles(realmId, 0, -1).forEach(r -> roleDao.deleteRole(r.getId()));
    roleDao.deleteAllRealmRoles(realmId);
  }

  @Override
  public void removeAllClientRoles(String clientId) {
    getAllClientRoles(clientId, 0, -1).forEach(r -> roleDao.deleteRole(r.getId()));
    roleDao.deleteAllClientRoles(clientId);
  }

  @Override
  public RealmRole getRealmRoleByName(String realmId, String name) {
    return roleDao.getRealmRoleByName(realmId, name);
  }

  @Override
  public ClientRole getClientRoleByName(String clientId, String name) {
    return roleDao.getClientRoleByName(clientId, name);
  }

  @Override
  public Stream<RealmRole> getAllRealmRoles(String realmId, Integer firstResult, Integer maxResult) {
    return paginated(roleDao.findAllRealmRoles(realmId), firstResult, maxResult);
  }

  @Override
  public Stream<ClientRole> getAllClientRoles(String clientId, Integer firstResult, Integer maxResult) {
    return paginated(roleDao.findAllClientRoles(clientId), firstResult, maxResult);
  }

  @Override
  public void updateAttribute(RoleToAttributeMapping attributeMapping) {
    RoleToAttributeMapping oldAttribute = roleDao.findAttribute(attributeMapping.getRoleId(), attributeMapping.getAttributeName());
    roleDao.insertOrUpdate(attributeMapping);

    if (oldAttribute != null) {
      // Alte AttributeToUserMappings löschen, da die Values als Teil des PartitionKey nicht
      // geändert werden können
      oldAttribute
          .getAttributeValues()
          .forEach(value -> roleDao.deleteAttributeToRoleMapping(new AttributeToRoleMapping(oldAttribute.getAttributeName(), value, oldAttribute.getRoleId())));
    }

    attributeMapping
        .getAttributeValues()
        .forEach(value -> {
          AttributeToRoleMapping attributeToRoleMapping = new AttributeToRoleMapping(attributeMapping.getAttributeName(), value, attributeMapping.getRoleId());
          roleDao.insert(attributeToRoleMapping);
        });
  }

  @Override
  public void deleteAttribute(String roleId, String attributeName) {
    RoleToAttributeMapping attribute = findRoleAttribute(roleId, attributeName);

    if (attribute == null) {
      return;
    }

    // Beide Mapping-Tabellen beachten!
    roleDao.deleteAttribute(roleId, attributeName);
    attribute
        .getAttributeValues()
        .forEach(value -> roleDao.deleteAttributeToRoleMapping(new AttributeToRoleMapping(attributeName, value, roleId)));
  }

  @Override
  public RoleToAttributeMapping findRoleAttribute(String roleId, String attributeName) {
    return roleDao.findAttribute(roleId, attributeName);
  }

  @Override
  public List<RoleToAttributeMapping> findAllRoleAttributes(String roleId) {
    return roleDao.findAllAttributes(roleId).all();
  }

  @Override
  public Role getRoleById(String id) {
    return roleDao.getRoleById(id);
  }

  @Override
  public Stream<Role> getRolesByIds(List<String> ids, Integer firstResult, Integer maxResult) {
    return paginated(roleDao.getRolesByIds(ids), firstResult, maxResult);
  }
}
