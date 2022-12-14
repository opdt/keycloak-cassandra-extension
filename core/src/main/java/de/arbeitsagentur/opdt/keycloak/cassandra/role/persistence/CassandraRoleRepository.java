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
import java.util.stream.Collectors;

import static de.arbeitsagentur.opdt.keycloak.cassandra.StreamExtensions.paginated;


@RequiredArgsConstructor
public class CassandraRoleRepository implements RoleRepository {
  private final RoleDao roleDao;


  @Override
  public void addOrUpdateRoles(Roles role) {
    roleDao.insertOrUpdate(role);
  }

  @Override
  public Roles getRolesByRealmId(String realmId) {
    Roles rolesByRealmId = roleDao.getRolesByRealmId(realmId);
    if(rolesByRealmId == null) {
      rolesByRealmId = Roles.builder().realmId(realmId).build();
    }

    return rolesByRealmId;
  }

  @Override
  public void deleteRealmRoles(String realmId) {
    roleDao.deleteAllRealmRoles(realmId);
  }
}
