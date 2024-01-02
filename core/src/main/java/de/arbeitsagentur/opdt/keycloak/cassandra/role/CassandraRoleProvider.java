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
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.RoleValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.Roles;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.CassandraModelTransaction;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static de.arbeitsagentur.opdt.keycloak.mapstorage.common.MapProviderObjectType.ROLE_AFTER_REMOVE;
import static de.arbeitsagentur.opdt.keycloak.mapstorage.common.MapProviderObjectType.ROLE_BEFORE_REMOVE;

@JBossLog
public class CassandraRoleProvider implements RoleProvider {
    private final RoleRepository roleRepository;
    private final KeycloakSession session;
    private final Map<String, Roles> rolesByRealmId = new HashMap<>();
    private final Set<String> rolesChanged = new HashSet<>();
    private final Set<String> rolesDeleted = new HashSet<>();

    public CassandraRoleProvider( KeycloakSession session, RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
        this.session = session;
    }

    public void markChanged(String realmId) {
        rolesChanged.add(realmId);
    }

    public void markDeleted(String realmId) {
        rolesDeleted.add(realmId);
    }

    private Roles getRoles(String realmId) {
        if(rolesByRealmId.containsKey(realmId)) {
            return rolesByRealmId.get(realmId);
        }

        Roles roles = roleRepository.getRolesByRealmId(realmId);
        rolesByRealmId.put(realmId, roles);

        session.getTransactionManager().enlistAfterCompletion((CassandraModelTransaction) () -> {
            if(rolesChanged.contains(realmId) && !rolesDeleted.contains(realmId)) {
                roleRepository.insertOrUpdate(roles);
            }

            rolesByRealmId.remove(realmId);
            rolesChanged.remove(realmId);
            rolesDeleted.remove(realmId);
        });

        return roles;
    }

    private Function<RoleValue, RoleModel> entityToAdapterFunc(RealmModel realm) {
        return origEntity -> origEntity == null ? null : new CassandraRoleAdapter(origEntity.getId(), realm, origEntity, getRoles(realm.getId()), this);
    }

    @Override
    public RoleModel addRealmRole(RealmModel realm, String id, String name) {
        if (getRealmRole(realm, name) != null) {
            throw new ModelDuplicateException("Role with the same name exists: " + name + " for realm " + realm.getName());
        }

        log.debugf("addRealmRole(%s, %s, %s)%s", realm, id, name, getShortStackTrace());

        Roles roles = getRoles(realm.getId());
        if (id != null && roles.getRoleById(id) != null) {
            throw new ModelDuplicateException("Role exists: " + id);
        }

        RoleValue role = RoleValue.builder()
            .id(id == null ? KeycloakModelUtils.generateId() : id)
            .name(name)
            .realmId(realm.getId())
            .build();


        roles.addRealmRole(role);
        markChanged(realm.getId());

        return entityToAdapterFunc(realm).apply(role);
    }

    @Override
    public Stream<RoleModel> getRealmRolesStream(RealmModel realm) {
        return getRealmRolesStream(realm, 0, -1);
    }

    @Override
    public Stream<RoleModel> getRealmRolesStream(RealmModel realm, Integer first, Integer max) {
        log.debugv("get all realm roles: realmId={0} first={1} max={2}", realm.getId(), first, max);

        Roles roles = getRoles(realm.getId());
        return roles
            .getRealmRoles(first, max).stream()
            .map(r -> entityToAdapterFunc(realm).apply(r));
    }

    @Override
    public Stream<RoleModel> getRolesStream(RealmModel realm, Stream<String> ids, String search, Integer first, Integer max) {
        log.debugf("get all realm roles: realmId=%s search=%s first=%s max=%s", realm.getId(), search, first, max);

        Roles roles = getRoles(realm.getId());
        return ids.map(roles::getRoleById)
            .filter(role -> search == null
                || search.isEmpty()
                || role.getName().toLowerCase().contains(search.toLowerCase())
                || role.getDescription().toLowerCase().contains(search.toLowerCase()))
            .map(entityToAdapterFunc(realm))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(RoleModel::getName))
            .skip(first == null || first < 0 ? 0 : first)
            .limit(max == null || max < 0 ? Long.MAX_VALUE : max);
    }

    @Override
    public boolean removeRole(RoleModel role) {
        log.debugf("removeRole roleId=%s", role.getId());

        boolean removed;
        RealmModel realm = role.isClientRole() ? ((ClientModel)role.getContainer()).getRealm() : (RealmModel)role.getContainer();

        session.invalidate(ROLE_BEFORE_REMOVE, realm, role);

        if (role.isClientRole()) {
            Roles roles = getRoles(realm.getId());
            removed = roles.removeClientRole(role.getContainerId(), role.getId());
            markChanged(realm.getId());
        } else {
            Roles roles = getRoles(role.getContainerId());
            removed = roles.removeRealmRole(role.getId());
            markChanged(role.getContainerId());
        }

        session.invalidate(ROLE_AFTER_REMOVE, realm, role);

        return removed;
    }

    @Override
    public void removeRoles(RealmModel realm) {
        log.debugf("removeRoles realmId=%s", realm.getId());

        getRealmRolesStream(realm).forEach(this::removeRole);
        markChanged(realm.getId());
    }

    @Override
    public void removeRoles(ClientModel client) {
        log.debugf("removeRoles clientId=%s", client.getId());

        getClientRolesStream(client).forEach(this::removeRole);
        markChanged(client.getRealm().getId());
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

        Roles roles = getRoles(client.getRealm().getId());
        if (id != null && roles.getRoleById(id) != null) {
            throw new ModelDuplicateException("Role exists: " + id);
        }

        RoleValue role = RoleValue.builder()
            .id(id == null ? KeycloakModelUtils.generateId() : id)
            .name(name)
            .clientId(client.getId())
            .build();


        roles.addClientRole(client.getId(), role);
        markChanged(client.getRealm().getId());

        return entityToAdapterFunc(client.getRealm()).apply(role);
    }

    @Override
    public Stream<RoleModel> getClientRolesStream(ClientModel client) {
        return RoleProvider.super.getClientRolesStream(client);
    }

    @Override
    public Stream<RoleModel> getClientRolesStream(ClientModel client, Integer first, Integer max) {
        log.debugv("get all client roles: clientId={0} first={1} max={2}", client.getId(), first, max);

        Roles roles = getRoles(client.getRealm().getId());
        return roles
            .getClientRoles(client.getId(), first, max).stream()
            .map(r -> entityToAdapterFunc(client.getRealm()).apply(r));
    }

    @Override
    public Stream<RoleModel> searchForClientRolesStream(ClientModel client, String search, Integer first, Integer max) {
        log.debugf("get all client roles: clientId=%s search=%s first=%s max=%s", client.getId(), search, first, max);

        Roles roles = getRoles(client.getRealm().getId());
        return roles.getClientRoles(client.getId(), first, max).stream()
            .filter(role -> search == null
                || search.isEmpty()
                || role.getName().toLowerCase().contains(search.toLowerCase())
                || role.getDescription().toLowerCase().contains(search.toLowerCase()))
            .map(entityToAdapterFunc(client.getRealm()));
    }

    @Override
    public Stream<RoleModel> searchForClientRolesStream(RealmModel realm, Stream<String> ids, String search, Integer first, Integer max) {
        List<RoleModel> result = new ArrayList<>();
        realm.getClientsStream().forEach(client -> {
            client.getRolesStream()
                .filter(role -> search == null
                    || search.isEmpty()
                    || role.getName().toLowerCase().contains(search.toLowerCase())
                    || client.getClientId().toLowerCase().contains(search.toLowerCase()))
                .filter(role -> ids == null || ids.anyMatch(i -> i.equals(role.getId())))
                .forEach(result::add);
            });

        return result.stream();
    }

    @Override
    public Stream<RoleModel> searchForClientRolesStream(RealmModel realm, String search, Stream<String> excludedIds, Integer first, Integer max) {
        List<RoleModel> result = new ArrayList<>();
        realm.getClientsStream().forEach(client -> {
            client.getRolesStream()
                .filter(role -> search == null
                    || search.isEmpty()
                    || role.getName().toLowerCase().contains(search.toLowerCase())
                    || client.getClientId().toLowerCase().contains(search.toLowerCase()))
                .filter(role -> excludedIds == null || excludedIds.noneMatch(i -> i.equals(role.getId())))
                .forEach(result::add);
        });

        return result.stream();
    }

    @Override
    public RoleModel getRealmRole(RealmModel realm, String name) {
        log.debugf("getRealmRole realmId=%s name=%s", realm.getId(), name);
        Roles roles = getRoles(realm.getId());
        RoleValue realmRole = roles.getRealmRoles().stream().filter(r -> Objects.equals(r.getName(), name)).findFirst().orElse(null);

        if (realmRole == null) {
            return null;
        }

        return entityToAdapterFunc(realm).apply(realmRole);
    }

    @Override
    public RoleModel getRoleById(RealmModel realm, String id) {
        log.debugf("getRoleById realmId=%s id=%s", realm.getId(), id);
        Roles roles = getRoles(realm.getId());
        RoleValue role = roles.getRoleById(id);

        if (role == null) {
            return null;
        }

        return entityToAdapterFunc(realm).apply(role);
    }

    @Override
    public Stream<RoleModel> searchForRolesStream(RealmModel realm, String search, Integer first, Integer max) {
        log.debugf("get all roles: realmId=%s search=%s first=%s max=%s", realm.getId(), search, first, max);

        Roles roles = getRoles(realm.getId());
        return roles.getRealmRoles().stream()
            .filter(role -> search == null
                || search.isEmpty()
                || role.getName().toLowerCase().contains(search.toLowerCase())
                || (role.getDescription() != null && role.getDescription().toLowerCase().contains(search.toLowerCase())))
            .map(entityToAdapterFunc(realm))
            .filter(Objects::nonNull)
            .skip(first == null || first < 0 ? 0 : first)
            .limit(max == null || max < 0 ? Long.MAX_VALUE : max);
    }

    @Override
    public RoleModel getClientRole(ClientModel client, String name) {
        log.debugf("getClientRole clientId=%s name=%s", client.getId(), name);
        Roles roles = getRoles(client.getRealm().getId());
        RoleValue clientRole = roles.getClientRoles().getOrDefault(client.getId(), new HashSet<>()).stream()
            .filter(r -> Objects.equals(r.getName(), name))
            .findFirst()
            .orElse(null);

        if (clientRole == null) {
            return null;
        }

        return entityToAdapterFunc(client.getRealm()).apply(clientRole);
    }
    public void preRemove(RealmModel realm) {
        removeRoles(realm);
    }

    public void preRemove(RealmModel realm, RoleModel role) {
        getRealmRolesStream(realm).forEach(r -> r.removeCompositeRole(role));
        realm.getClientsStream().flatMap(this::getClientRolesStream).forEach(r -> r.removeCompositeRole(role));
    }

    @Override
    public void close() {
        rolesByRealmId.clear();
        rolesChanged.clear();
        rolesDeleted.clear();
    }
}
