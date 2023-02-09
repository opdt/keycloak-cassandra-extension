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
package de.arbeitsagentur.opdt.keycloak.cassandra.realm;

import de.arbeitsagentur.opdt.keycloak.cassandra.AbstractCassandraProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.CompositeRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.cache.ThreadLocalCache;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.ClientInitialAccess;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.Realm;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.REALM_AFTER_REMOVE;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.REALM_BEFORE_REMOVE;
import static org.keycloak.models.map.common.ExpirationUtils.isExpired;

@JBossLog
public class CassandraRealmsProvider extends AbstractCassandraProvider implements RealmProvider {
    private KeycloakSession session;
    private RealmRepository realmRepository;

    public CassandraRealmsProvider(KeycloakSession session, CompositeRepository cassandraRepository) {
        this.session = session;
        this.realmRepository = cassandraRepository;
    }

    private RealmModel entityToAdapter(Realm entity) {
        return entity == null ? null : new CassandraRealmAdapter(session, entity, realmRepository);
    }

    @Override
    public RealmModel createRealm(String name) {
        return createRealm(KeycloakModelUtils.generateId(), name);
    }

    @Override
    public RealmModel createRealm(String id, String name) {
        if (getRealmByName(name) != null) {
            throw new ModelDuplicateException("Realm with given name exists: " + name);
        }

        Realm existingRealm = realmRepository.getRealmById(id);
        if (existingRealm != null) {
            throw new ModelDuplicateException("Realm exists: " + id);
        }

        log.tracef("createRealm(%s, %s)%s", id, name, getShortStackTrace());

        Realm realm = new Realm(id, name, new HashMap<>());
        realmRepository.createRealm(realm);
        RealmModel realmModel = entityToAdapter(realm);
        realmModel.setName(name);

        return realmModel;
    }

    @Override
    public RealmModel getRealm(String id) {
        if (id == null) return null;

        log.tracef("getRealm(%s)%s", id, getShortStackTrace());

        Realm realm = realmRepository.getRealmById(id);
        return entityToAdapter(realm);
    }

    @Override
    public RealmModel getRealmByName(String name) {
        if (name == null) return null;

        log.tracef("getRealm(%s)%s", name, getShortStackTrace());

        Realm realm = realmRepository.findRealmByName(name);
        return entityToAdapter(realm);
    }

    @Override
    public Stream<RealmModel> getRealmsStream() {
        return realmRepository.getAllRealms().stream().map(this::entityToAdapter);
    }

    @Override
    public Stream<RealmModel> getRealmsWithProviderTypeStream(Class<?> type) {
        return realmRepository.getAllRealms().stream()
            .filter(r -> r.getAttributes().containsKey(CassandraRealmAdapter.COMPONENT_PROVIDER_TYPE))
            .filter(r -> r.getAttributes().get(CassandraRealmAdapter.COMPONENT_PROVIDER_TYPE).contains(type.getName()))
            .map(this::entityToAdapter);
    }

    @Override
    public boolean removeRealm(String id) {
        log.tracef("removeRealm(%s)%s", id, getShortStackTrace());
        Realm realm = realmRepository.getRealmById(id);

        if (realm == null) return false;

        RealmModel realmModel = getRealm(id);
        session.invalidate(REALM_BEFORE_REMOVE, realmModel);
        realmRepository.deleteRealm(realm);
        session.invalidate(REALM_AFTER_REMOVE, realmModel);

        return true;
    }

    @Override
    public void removeExpiredClientInitialAccess() {
        List<ClientInitialAccess> cias = realmRepository.getAllClientInitialAccesses();
        if (cias != null)
            cias.stream()
                .filter(this::checkIfExpired)
                .collect(Collectors.toSet())
                .forEach(realmRepository::deleteClientInitialAccess);
    }

    private boolean checkIfExpired(ClientInitialAccess cia) {
        return cia.getRemainingCount() < 1 || isExpired(cia, true);
    }

    @Override
    public void saveLocalizationText(RealmModel realm, String locale, String key, String text) {
        if (locale == null || key == null || text == null) return;

        Map<String, String> current = realm.getRealmLocalizationTextsByLocale(locale);

        if (current == null) {
            current = new HashMap<>();
        }

        current.put(key, text);
        realm.createOrUpdateRealmLocalizationTexts(locale, current);
    }


    @Override
    public void saveLocalizationTexts(RealmModel realm, String locale, Map<String, String> textMap) {
        if (locale == null || textMap == null) return;

        realm.createOrUpdateRealmLocalizationTexts(locale, textMap);
    }

    @Override
    public boolean updateLocalizationText(RealmModel realm, String locale, String key, String text) {
        if (locale == null || key == null || text == null || (!realm.getRealmLocalizationTextsByLocale(locale).containsKey(key))) return false;

        Map<String, String> realmLocalizationTextsByLocale = realm.getRealmLocalizationTextsByLocale(locale);
        if (realmLocalizationTextsByLocale == null || !realmLocalizationTextsByLocale.containsKey(key)) {
            return false;
        }

        realmLocalizationTextsByLocale.put(key, text);
        realm.createOrUpdateRealmLocalizationTexts(locale, realmLocalizationTextsByLocale);
        return true;
    }

    @Override
    public boolean deleteLocalizationTextsByLocale(RealmModel realm, String locale) {
        return realm.removeRealmLocalizationTexts(locale);
    }

    @Override
    public boolean deleteLocalizationText(RealmModel realm, String locale, String key) {
        if (locale == null || key == null || (!realm.getRealmLocalizationTextsByLocale(locale).containsKey(key))) return false;

        Map<String, String> realmLocalizationTextsByLocale = realm.getRealmLocalizationTextsByLocale(locale);
        if (realmLocalizationTextsByLocale == null || !realmLocalizationTextsByLocale.containsKey(key)) {
            return false;
        }

        realmLocalizationTextsByLocale.remove(key);
        realm.createOrUpdateRealmLocalizationTexts(locale, realmLocalizationTextsByLocale);
        return true;
    }

    @Override
    public String getLocalizationTextsById(RealmModel realm, String locale, String key) {
        if (locale == null || key == null || (!realm.getRealmLocalizationTextsByLocale(locale).containsKey(key))) return null;
        return realm.getRealmLocalizationTextsByLocale(locale).get(key);
    }

    @Override
    @Deprecated
    public ClientModel addClient(RealmModel realm, String id, String clientId) {
        return session.clients().addClient(realm, id, clientId);
    }

    @Override
    @Deprecated
    public long getClientsCount(RealmModel realm) {
        return session.clients().getClientsCount(realm);
    }

    @Override
    @Deprecated
    public Stream<ClientModel> getClientsStream(RealmModel realm, Integer firstResult, Integer maxResults) {
        return session.clients().getClientsStream(realm, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public Stream<ClientModel> getAlwaysDisplayInConsoleClientsStream(RealmModel realm) {
        return session.clients().getAlwaysDisplayInConsoleClientsStream(realm);
    }

    @Override
    @Deprecated
    public boolean removeClient(RealmModel realm, String id) {
        return session.clients().removeClient(realm, id);
    }

    @Override
    @Deprecated
    public void removeClients(RealmModel realm) {
        session.clients().removeClients(realm);
    }

    @Override
    @Deprecated
    public ClientModel getClientById(RealmModel realm, String id) {
        return session.clients().getClientById(realm, id);
    }

    @Override
    @Deprecated
    public ClientModel getClientByClientId(RealmModel realm, String clientId) {
        return session.clients().getClientByClientId(realm, clientId);
    }

    @Override
    @Deprecated
    public Stream<ClientModel> searchClientsByClientIdStream(RealmModel realm, String clientId, Integer firstResult, Integer maxResults) {
        return session.clients().searchClientsByClientIdStream(realm, clientId, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public Stream<ClientModel> searchClientsByAttributes(RealmModel realm, Map<String, String> attributes, Integer firstResult, Integer maxResults) {
        return session.clients().searchClientsByAttributes(realm, attributes, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public void addClientScopes(RealmModel realm, ClientModel client, Set<ClientScopeModel> clientScopes, boolean defaultScope) {
        session.clients().addClientScopes(realm, client, clientScopes, defaultScope);
    }

    @Override
    @Deprecated
    public void removeClientScope(RealmModel realm, ClientModel client, ClientScopeModel clientScope) {
        session.clients().removeClientScope(realm, client, clientScope);
    }

    @Override
    @Deprecated
    public Map<String, ClientScopeModel> getClientScopes(RealmModel realm, ClientModel client, boolean defaultScopes) {
        return session.clients().getClientScopes(realm, client, defaultScopes);
    }

    @Override
    @Deprecated
    public ClientScopeModel getClientScopeById(RealmModel realm, String id) {
        return session.clientScopes().getClientScopeById(realm, id);
    }

    @Override
    @Deprecated
    public Stream<ClientScopeModel> getClientScopesStream(RealmModel realm) {
        return session.clientScopes().getClientScopesStream(realm);
    }

    @Override
    @Deprecated
    public ClientScopeModel addClientScope(RealmModel realm, String id, String name) {
        return session.clientScopes().addClientScope(realm, id, name);
    }

    @Override
    @Deprecated
    public boolean removeClientScope(RealmModel realm, String id) {
        return session.clientScopes().removeClientScope(realm, id);
    }

    @Override
    @Deprecated
    public void removeClientScopes(RealmModel realm) {
        session.clientScopes().removeClientScopes(realm);
    }

    @Override
    @Deprecated
    public Map<ClientModel, Set<String>> getAllRedirectUrisOfEnabledClients(RealmModel realm) {
        return session.clients().getAllRedirectUrisOfEnabledClients(realm);
    }

    @Override
    @Deprecated
    public void moveGroup(RealmModel realm, GroupModel group, GroupModel toParent) {
        session.groups().moveGroup(realm, group, toParent);
    }

    @Override
    @Deprecated
    public GroupModel getGroupById(RealmModel realm, String id) {
        return session.groups().getGroupById(realm, id);
    }

    @Override
    @Deprecated
    public Long getGroupsCount(RealmModel realm, Boolean onlyTopGroups) {
        return session.groups().getGroupsCount(realm, onlyTopGroups);
    }

    @Override
    @Deprecated
    public Long getGroupsCountByNameContaining(RealmModel realm, String search) {
        return session.groups().getGroupsCountByNameContaining(realm, search);
    }

    @Override
    @Deprecated
    public boolean removeGroup(RealmModel realm, GroupModel group) {
        return session.groups().removeGroup(realm, group);
    }

    @Override
    @Deprecated
    public GroupModel createGroup(RealmModel realm, String id, String name, GroupModel toParent) {
        return session.groups().createGroup(realm, id, name, toParent);
    }

    @Override
    @Deprecated
    public void addTopLevelGroup(RealmModel realm, GroupModel subGroup) {
        session.groups().addTopLevelGroup(realm, subGroup);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> getGroupsStream(RealmModel realm) {
        return session.groups().getGroupsStream(realm);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> getGroupsStream(RealmModel realm, Stream<String> ids, String search, Integer first, Integer max) {
        return session.groups().getGroupsStream(realm, ids, search, first, max);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> getGroupsByRoleStream(RealmModel realm, RoleModel role, Integer firstResult, Integer maxResults) {
        return session.groups().getGroupsByRoleStream(realm, role, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> getTopLevelGroupsStream(RealmModel realm) {
        return session.groups().getTopLevelGroupsStream(realm);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> getTopLevelGroupsStream(RealmModel realm, Integer firstResult, Integer maxResults) {
        return session.groups().getTopLevelGroupsStream(realm, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> searchForGroupByNameStream(RealmModel realm, String search, Boolean exact, Integer firstResult, Integer maxResults) {
        return session.groups().searchForGroupByNameStream(realm, search, exact, firstResult, maxResults);
    }

    @Override
    public Stream<GroupModel> searchGroupsByAttributes(RealmModel realm, Map<String, String> attributes, Integer firstResult, Integer maxResults) {
        return session.groups().searchGroupsByAttributes(realm, attributes, firstResult, maxResults);
    }

    @Override
    @Deprecated
    public RoleModel addRealmRole(RealmModel realm, String id, String name) {
        return session.roles().addRealmRole(realm, id, name);
    }

    @Override
    @Deprecated
    public RoleModel getRealmRole(RealmModel realm, String name) {
        return session.roles().getRealmRole(realm, name);
    }

    @Override
    @Deprecated
    public Stream<RoleModel> getRealmRolesStream(RealmModel realm, Integer first, Integer max) {
        return session.roles().getRealmRolesStream(realm, first, max);
    }

    @Override
    public Stream<RoleModel> getRolesStream(RealmModel realm, Stream<String> ids, String search, Integer first, Integer max) {
        return session.roles().getRolesStream(realm, ids, search, first, max);
    }

    @Override
    @Deprecated
    public boolean removeRole(RoleModel role) {
        return session.roles().removeRole(role);
    }

    @Override
    @Deprecated
    public void removeRoles(RealmModel realm) {
        session.roles().removeRoles(realm);
    }

    @Override
    @Deprecated
    public RoleModel addClientRole(ClientModel client, String id, String name) {
        return session.roles().addClientRole(client, name);
    }

    @Override
    @Deprecated
    public Stream<RoleModel> getClientRolesStream(ClientModel client, Integer first, Integer max) {
        return session.roles().getClientRolesStream(client, first, max);
    }

    @Override
    @Deprecated
    public void removeRoles(ClientModel client) {
        session.roles().removeRoles(client);
    }

    @Override
    @Deprecated
    public RoleModel getRoleById(RealmModel realm, String id) {
        return session.roles().getRoleById(realm, id);
    }

    @Override
    @Deprecated
    public Stream<RoleModel> searchForRolesStream(RealmModel realm, String search, Integer first, Integer max) {
        return session.roles().searchForRolesStream(realm, search, first, max);
    }

    @Override
    @Deprecated
    public RoleModel getClientRole(ClientModel client, String name) {
        return session.roles().getClientRole(client, name);
    }

    @Override
    @Deprecated
    public Stream<RoleModel> searchForClientRolesStream(ClientModel client, String search, Integer first, Integer max) {
        return session.roles().searchForClientRolesStream(client, search, first, max);
    }

    @Override
    protected List<String> getCacheNames() {
        return Arrays.asList(ThreadLocalCache.REALM_CACHE);
    }
}
