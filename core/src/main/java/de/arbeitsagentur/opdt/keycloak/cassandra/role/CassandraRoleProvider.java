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

import de.arbeitsagentur.opdt.keycloak.cassandra.AbstractCassandraProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.cache.ThreadLocalCache;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.RoleRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.RoleValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.Roles;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.HashSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;

@JBossLog
public class CassandraRoleProvider extends AbstractCassandraProvider implements RoleProvider {
    private final RoleRepository roleRepository;

    public CassandraRoleProvider(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    private Function<RoleValue, RoleModel> entityToAdapterFunc(RealmModel realm) {
        return origEntity -> origEntity == null ? null : new CassandraRoleAdapter(realm, origEntity, roleRepository);
    }

    @Override
    public RoleModel addRealmRole(RealmModel realm, String id, String name) {
        if (getRealmRole(realm, name) != null) {
            throw new ModelDuplicateException("Role with the same name exists: " + name + " for realm " + realm.getName());
        }

        log.debugf("addRealmRole(%s, %s, %s)%s", realm, id, name, getShortStackTrace());

        Roles roles = roleRepository.getRolesByRealmId(realm.getId());
        if (id != null && roles.getRoleById(id) != null) {
            throw new ModelDuplicateException("Role exists: " + id);
        }

        RoleValue role = RoleValue.builder()
            .id(id == null ? KeycloakModelUtils.generateId() : id)
            .name(name)
            .realmId(realm.getId())
            .build();


        roles.addRealmRole(role);
        roleRepository.addOrUpdateRoles(roles);

        return entityToAdapterFunc(realm).apply(role);
    }

    @Override
    public Stream<RoleModel> getRealmRolesStream(RealmModel realm) {
        return getRealmRolesStream(realm, 0, -1);
    }

    @Override
    public Stream<RoleModel> getRealmRolesStream(RealmModel realm, Integer first, Integer max) {
        log.debugv("get all realm roles: realmId={0} first={1} max={2}", realm.getId(), first, max);

        Roles roles = roleRepository.getRolesByRealmId(realm.getId());
        return roles
            .getRealmRoles(first, max).stream()
            .map(r -> entityToAdapterFunc(realm).apply(r));
    }

    @Override
    public Stream<RoleModel> getRolesStream(RealmModel realm, Stream<String> ids, String search, Integer first, Integer max) {
        log.debugf("get all realm roles: realmId=%s search=%s first=%s max=%s", realm.getId(), search, first, max);

        Roles roles = roleRepository.getRolesByRealmId(realm.getId());
        return ids.map(roles::getRoleById)
            .filter(role -> search == null
                || search.isEmpty()
                || role.getName().toLowerCase().contains(search.toLowerCase())
                || role.getDescription().toLowerCase().contains(search.toLowerCase()))
            .map(entityToAdapterFunc(realm));
    }

    @Override
    public boolean removeRole(RoleModel role) {
        log.debugf("removeRole roleId=%s", role.getId());

        if (role.isClientRole()) {
            String realmId = ((ClientModel) role.getContainer()).getRealm().getId();
            Roles roles = roleRepository.getRolesByRealmId(realmId);
            boolean removed = roles.removeClientRole(role.getContainerId(), role.getId());

            if (removed) {
                roleRepository.addOrUpdateRoles(roles);
            }
            return removed;
        } else {
            Roles roles = roleRepository.getRolesByRealmId(role.getContainerId());
            boolean removed = roles.removeRealmRole(role.getId());

            if (removed) {
                roleRepository.addOrUpdateRoles(roles);
            }
            return removed;
        }
    }

    @Override
    public void removeRoles(RealmModel realm) {
        log.debugf("removeRoles realmId=%s", realm.getId());

        Roles roles = roleRepository.getRolesByRealmId(realm.getId());
        roles.getRealmRoles().clear();
        roleRepository.addOrUpdateRoles(roles);
    }

    @Override
    public RoleModel addClientRole(ClientModel client, String name) {
        return addClientRole(client, null, name);
    }

    @Override
    public RoleModel addClientRole(ClientModel client, String id, String name) {
        if (getClientRole(client, name) != null) {
            throw new ModelDuplicateException("Role with the same name exists: " + name + " for client " + client.getClientId());
        }

        log.debugf("addClientRole(%s, %s, %s)%s", client.getClientId(), id, name, getShortStackTrace());

        Roles roles = roleRepository.getRolesByRealmId(client.getRealm().getId());
        if (id != null && roles.getRoleById(id) != null) {
            throw new ModelDuplicateException("Role exists: " + id);
        }

        RoleValue role = RoleValue.builder()
            .id(id == null ? KeycloakModelUtils.generateId() : id)
            .name(name)
            .clientId(client.getId())
            .build();


        roles.addClientRole(client.getId(), role);
        roleRepository.addOrUpdateRoles(roles);

        return entityToAdapterFunc(client.getRealm()).apply(role);
    }

    @Override
    public Stream<RoleModel> getClientRolesStream(ClientModel client) {
        return RoleProvider.super.getClientRolesStream(client);
    }

    @Override
    public Stream<RoleModel> getClientRolesStream(ClientModel client, Integer first, Integer max) {
        log.debugv("get all client roles: clientId={0} first={1} max={2}", client.getId(), first, max);

        Roles roles = roleRepository.getRolesByRealmId(client.getRealm().getId());
        return roles
            .getClientRoles(client.getId(), first, max).stream()
            .map(r -> entityToAdapterFunc(client.getRealm()).apply(r));
    }

    @Override
    public Stream<RoleModel> searchForClientRolesStream(ClientModel client, String search, Integer first, Integer max) {
        log.debugf("get all client roles: clientId=%s search=%s first=%s max=%s", client.getId(), search, first, max);

        Roles roles = roleRepository.getRolesByRealmId(client.getRealm().getId());
        return roles.getClientRoles(client.getId(), first, max).stream()
            .filter(role -> search == null
                || search.isEmpty()
                || role.getName().toLowerCase().contains(search.toLowerCase())
                || role.getDescription().toLowerCase().contains(search.toLowerCase()))
            .map(entityToAdapterFunc(client.getRealm()));
    }

    @Override
    public void removeRoles(ClientModel client) {
        log.debugf("removeRoles clientId=%s", client.getId());
        Roles roles = roleRepository.getRolesByRealmId(client.getRealm().getId());
        roles.getClientRoles().getOrDefault(client.getId(), new HashSet<>()).clear();
        roleRepository.addOrUpdateRoles(roles);
    }

    @Override
    public RoleModel getRealmRole(RealmModel realm, String name) {
        log.debugf("getRealmRole realmId=%s name=%s", realm.getId(), name);
        Roles roles = roleRepository.getRolesByRealmId(realm.getId());
        RoleValue realmRole = roles.getRealmRoles().stream().filter(r -> Objects.equals(r.getName(), name)).findFirst().orElse(null);

        if (realmRole == null) {
            return null;
        }

        return entityToAdapterFunc(realm).apply(realmRole);
    }

    @Override
    public RoleModel getRoleById(RealmModel realm, String id) {
        log.debugf("getRoleById realmId=%s id=%s", realm.getId(), id);
        Roles roles = roleRepository.getRolesByRealmId(realm.getId());
        RoleValue role = roles.getRoleById(id);

        if (role == null) {
            return null;
        }

        return entityToAdapterFunc(realm).apply(role);
    }

    @Override
    public Stream<RoleModel> searchForRolesStream(RealmModel realm, String search, Integer first, Integer max) {
        log.debugf("get all roles: realmId=%s search=%s first=%s max=%s", realm.getId(), search, first, max);

        Roles roles = roleRepository.getRolesByRealmId(realm.getId());
        return roles.getRealmRoles(first, max).stream()
            .filter(role -> search == null
                || search.isEmpty()
                || role.getName().toLowerCase().contains(search.toLowerCase())
                || role.getDescription().toLowerCase().contains(search.toLowerCase()))
            .map(entityToAdapterFunc(realm));
    }

    @Override
    public RoleModel getClientRole(ClientModel client, String name) {
        log.debugf("getClientRole clientId=%s name=%s", client.getId(), name);
        Roles roles = roleRepository.getRolesByRealmId(client.getRealm().getId());
        RoleValue clientRole = roles.getClientRoles().getOrDefault(client.getId(), new HashSet<>()).stream()
            .filter(r -> Objects.equals(r.getName(), name))
            .findFirst()
            .orElse(null);

        if (clientRole == null) {
            return null;
        }

        return entityToAdapterFunc(client.getRealm()).apply(clientRole);
    }

    @Override
    protected String getCacheName() {
        return ThreadLocalCache.ROLE_CACHE;
    }
}
