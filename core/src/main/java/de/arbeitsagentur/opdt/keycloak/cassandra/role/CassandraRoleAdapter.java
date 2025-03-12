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

import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.RoleValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.Roles;
import java.util.*;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleContainerModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.utils.KeycloakModelUtils;

@EqualsAndHashCode(of = "roleId")
@JBossLog
@RequiredArgsConstructor
public class CassandraRoleAdapter implements RoleModel {
    private final String roleId;
    private final RealmModel realm;
    private final RoleValue role;
    private final Roles roles;
    private final CassandraRoleProvider provider;

    public RoleValue getRole() {
        return role;
    }

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
        provider.markChanged(realm.getId());
    }

    @Override
    public String getId() {
        return role.getId();
    }

    @Override
    public void setName(String name) {
        role.setName(name);
        provider.markChanged(realm.getId());
    }

    @Override
    public boolean isComposite() {
        return !this.role.getChildRoles().isEmpty();
    }

    @Override
    public void addCompositeRole(RoleModel roleToAdd) {
        log.debugv(
                "add composite Role: roleNameOrigin={0} roleNameTarget={1}", this.role.getName(), roleToAdd.getName());

        if (roles.getRoleById(roleToAdd.getId()) == null) {
            RoleValue toAdd = RoleValue.builder()
                    .id(roleToAdd.getId())
                    .realmId(realm.getId())
                    .clientId(roleToAdd.isClientRole() ? roleToAdd.getContainerId() : null)
                    .name(roleToAdd.getName())
                    .attributes(roleToAdd.getAttributes())
                    .description(roleToAdd.getDescription())
                    .build();

            if (roleToAdd.isClientRole()) {
                roles.addClientRole(roleToAdd.getContainerId(), toAdd);
            } else {
                roles.addRealmRole(toAdd);
            }
        }

        if (!this.role.getChildRoles().contains(roleToAdd.getId())) {
            List<String> newRoles = new ArrayList<>(this.role.getChildRoles());
            newRoles.add(roleToAdd.getId());
            this.role.setChildRoles(newRoles);
        }

        provider.markChanged(realm.getId());
    }

    @Override
    public void removeCompositeRole(RoleModel roleToDelete) {
        log.debugv(
                "remove composite Role: roleNameOrigin={0} roleNameTarget={1}",
                this.role.getName(), roleToDelete.getName());
        role.getChildRoles().remove(roleToDelete.getId());
        provider.markChanged(realm.getId());
    }

    @Override
    public Stream<RoleModel> getCompositesStream() {
        return getCompositesStream(null, 0, -1);
    }

    @Override
    public Stream<RoleModel> getCompositesStream(String search, Integer first, Integer max) {
        log.debugv("get composites: roleId={0} search={1} first={2} max={3}", role.getId(), search, first, max);

        return role.getChildRoles().stream()
                .map(roles::getRoleById)
                .filter(role -> search == null
                        || search.isEmpty()
                        || role.getName().toLowerCase().contains(search.toLowerCase())
                        || role.getDescription().toLowerCase().contains(search.toLowerCase()))
                .filter(Objects::nonNull)
                .map(r -> new CassandraRoleAdapter(r.getId(), realm, r, roles, provider))
                .map(RoleModel.class::cast)
                .sorted(Comparator.comparing(RoleModel::getName))
                .skip(first == null || first < 0 ? 0 : first)
                .limit(max == null || max < 0 ? Long.MAX_VALUE : max);
    }

    @Override
    public boolean isClientRole() {
        return role.getClientId() != null;
    }

    @Override
    public String getContainerId() {
        return role.getClientId() != null ? role.getClientId() : realm.getId();
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

        role.getAttributes().put(name, Collections.singletonList(value));
        provider.markChanged(realm.getId());
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        log.debugv("set attribute: roleId={0} name={1} value={2}", role.getId(), name, values);

        role.getAttributes().put(name, values);
        provider.markChanged(realm.getId());
    }

    @Override
    public void removeAttribute(String name) {
        log.debugv("remove attribute: roleId={0} name={1}", role.getId(), name);
        role.getAttributes().remove(name);
        provider.markChanged(realm.getId());
    }

    @Override
    public String getFirstAttribute(String name) {
        return getAttributeStream(name).findFirst().orElse(null);
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        log.debugv("get attribute: roleId={0} name={1}", role.getId(), name);

        return role.getAttributes().getOrDefault(name, Collections.emptyList()).stream();
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        log.debugv("get all attributes: roleId={0", role.getId());

        return role.getAttributes();
    }
}
