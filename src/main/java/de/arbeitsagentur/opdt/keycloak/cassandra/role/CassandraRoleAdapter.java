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
package de.arbeitsagentur.opdt.keycloak.cassandra.role;

import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.RoleRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.RoleToAttributeMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.Role;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleContainerModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EqualsAndHashCode(of = "role")
@JBossLog
@RequiredArgsConstructor
public class CassandraRoleAdapter implements RoleModel {
  private final RealmModel realm;
  private final Role role;
  private final RoleRepository roleRepository;

  @Override
  public String getName() {
    return role.getName();
  }

  @Override
  public String getDescription() {
    return role.getDescription();
  }

  @Override
  public void setDescription(String description) {
    role.setDescription(description);
    roleRepository.addOrUpdateRole(role);
  }

  @Override
  public String getId() {
    return role.getId();
  }

  @Override
  public void setName(String name) {
    role.setName(name);
    roleRepository.addOrUpdateRole(role);
  }

  @Override
  public boolean isComposite() {
    return !this.role.getChildRoles().isEmpty();
  }

  @Override
  public void addCompositeRole(RoleModel roleToAdd) {
    log.debugv("add composite Role: roleNameOrigin={0} roleNameTarget={1}", this.role.getName(), roleToAdd.getName());
    List<String> childRoles = new ArrayList<>(this.role.getChildRoles());

    childRoles.add(roleToAdd.getId());
    this.role.setChildRoles(childRoles);

    roleRepository.addOrUpdateRole(this.role);

    Role compositeRole = roleRepository.getRoleById(roleToAdd.getId());

    if (compositeRole == null) {
      log.debugv("Composite Role is null! Creating... : name={0}", roleToAdd.getName());
      compositeRole = new Role(
          roleToAdd.getId(),
          roleToAdd.getName(),
          roleToAdd.getDescription(),
          roleToAdd.isClientRole(),
          roleToAdd.isClientRole() ? roleToAdd.getContainer().getId() : null,
          realm.getId(),
          Collections.emptyList());
      roleRepository.addOrUpdateRole(compositeRole);
    }
  }

  @Override
  public void removeCompositeRole(RoleModel roleToDelete) {
    log.debugv("remove composite Role: roleNameOrigin={0} roleNameTarget={1}", this.role.getName(), roleToDelete.getName());
    this.role.getChildRoles().remove(roleToDelete.getId());
    roleRepository.addOrUpdateRole(this.role);
  }

  @Override
  public Stream<RoleModel> getCompositesStream() {
    return getCompositesStream(null, 0, -1);
  }

  @Override
  public Stream<RoleModel> getCompositesStream(String search, Integer first, Integer max) {
    log.debugv("get composites: roleId={0} search={1} first={2} max={3}", role.getId(), search, first, max);

    return role.getChildRoles().stream()
        .map(roleRepository::getRoleById)
        .filter(role -> search == null
            || search.isEmpty()
            || role.getName().toLowerCase().contains(search.toLowerCase())
            || role.getDescription().toLowerCase().contains(search.toLowerCase()))
        .map(r -> new CassandraRoleAdapter(realm, r, roleRepository));
  }

  @Override
  public boolean isClientRole() {
    return role.isClientRole();
  }

  @Override
  public String getContainerId() {
    return role.isClientRole() ? role.getClientId() : realm.getId();
  }

  @Override
  public RoleContainerModel getContainer() {
    return this.isClientRole() ? this.realm.getClientById(this.role.getClientId()) : this.realm;
  }

  @Override
  public boolean hasRole(RoleModel role) {
    return this.getId().equals(role.getId()) || KeycloakModelUtils.searchFor(role, this, new HashSet<>());
  }

  @Override
  public void setSingleAttribute(String name, String value) {
    log.debugv("set attribute: roleId={0} name={1} value={2}", role.getId(), name, value);

    RoleToAttributeMapping attribute = new RoleToAttributeMapping(role.getId(), name, Collections.singletonList(value));
    roleRepository.updateAttribute(attribute);
  }

  @Override
  public void setAttribute(String name, List<String> values) {
    log.debugv("set attribute: roleId={0} name={1} value={2}", role.getId(), name, values);

    RoleToAttributeMapping attribute = new RoleToAttributeMapping(role.getId(), name, values);
    roleRepository.updateAttribute(attribute);
  }

  @Override
  public void removeAttribute(String name) {
    log.debugv("remove attribute: roleId={0} name={1}", role.getId(), name);
    roleRepository.deleteAttribute(role.getId(), name);
  }

  @Override
  public String getFirstAttribute(String name) {
    return getAttributeStream(name).findFirst().orElse(null);
  }

  @Override
  public Stream<String> getAttributeStream(String name) {
    log.debugv("get attribute: roleId={0} name={1}", role.getId(), name);

    RoleToAttributeMapping attribute = roleRepository.findRoleAttribute(role.getId(), name);
    return attribute.getAttributeValues().stream();
  }

  @Override
  public Map<String, List<String>> getAttributes() {
    log.debugv("get all attributes: roleId={0", role.getId());

    return roleRepository.findAllRoleAttributes(role.getId()).stream()
        .collect(Collectors.toMap(RoleToAttributeMapping::getRoleId, RoleToAttributeMapping::getAttributeValues));
  }
}
