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
package de.arbeitsagentur.opdt.keycloak.cassandra.clientScope;

import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.ClientScopeRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopeValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopes;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.CassandraModelTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static de.arbeitsagentur.opdt.keycloak.mapstorage.common.MapProviderObjectType.CLIENT_SCOPE_AFTER_REMOVE;
import static de.arbeitsagentur.opdt.keycloak.mapstorage.common.MapProviderObjectType.CLIENT_SCOPE_BEFORE_REMOVE;

@JBossLog
@RequiredArgsConstructor
public class CassandraClientScopeProvider implements ClientScopeProvider {
    private final KeycloakSession session;

    private final ClientScopeRepository repository;

    private final Map<String, ClientScopes> scopesByRealmId = new HashMap<>();
    private final Set<String> scopesChanged = new HashSet<>();
    private final Set<String> scopesDeleted = new HashSet<>();


    public void markChanged(String realmId) {
        scopesChanged.add(realmId);
    }

    public void markDeleted(String realmId) {
        scopesDeleted.add(realmId);
    }

    private ClientScopes getScopes(String realmId) {
        if(scopesByRealmId.containsKey(realmId)) {
            return scopesByRealmId.get(realmId);
        }

        ClientScopes scopes = repository.getClientScopesByRealmId(realmId);
        scopesByRealmId.put(realmId, scopes);

        session.getTransactionManager().enlistAfterCompletion((CassandraModelTransaction) () -> {
            if(scopesChanged.contains(realmId) && !scopesDeleted.contains(realmId)) {
                repository.insertOrUpdate(scopes);
            }

            scopesByRealmId.remove(realmId);
            scopesChanged.remove(realmId);
        });

        return scopes;
    }

    private Function<ClientScopeValue, ClientScopeModel> entityToAdapterFunc(RealmModel realm) {
        return origEntity -> origEntity == null ? null : new CassandraClientScopeAdapter(realm, origEntity, getScopes(realm.getId()), repository, this);
    }

    @Override
    public Stream<ClientScopeModel> getClientScopesStream(RealmModel realm) {
        return getScopes(realm.getId())
            .getClientScopes().stream()
            .map(entityToAdapterFunc(realm));
    }

    @Override
    public ClientScopeModel addClientScope(RealmModel realm, String id, String name) {
        if (getClientScopeById(realm, id) != null) {
            throw new ModelDuplicateException("Client scope exists: " + id);
        }

        ClientScopes clientScopes = getScopes(realm.getId());
        ClientScopeValue existingClientScopeWithNameAndRealm = clientScopes.getClientScopes().stream()
            .filter(s -> Objects.equals(s.getName(), name)).findFirst().orElse(null);
        if (existingClientScopeWithNameAndRealm != null) {
            throw new ModelDuplicateException("Client scope with name '" + name + "' in realm " + realm.getName());
        }

        log.tracef("addClientScope(%s, %s, %s)%s", realm, id, name, getShortStackTrace());

        ClientScopeValue clientScopeValue = ClientScopeValue.builder()
            .id(id == null ? KeycloakModelUtils.generateId() : id)
            .realmId(realm.getId())
            .name(name)
            .build();

        clientScopes.addClientScope(clientScopeValue);
        markChanged(realm.getId());

        return entityToAdapterFunc(realm).apply(clientScopeValue);
    }

    @Override
    public boolean removeClientScope(RealmModel realm, String id) {
        if (id == null) return false;
        ClientScopes clientScopes = getScopes(realm.getId());
        if (clientScopes == null) return false;

        ClientScopeModel clientScopeModel = getClientScopeById(realm, id);
        session.invalidate(CLIENT_SCOPE_BEFORE_REMOVE, realm, clientScopeModel);

        boolean result = clientScopes.removeClientScope(id);
        markChanged(realm.getId());

        session.invalidate(CLIENT_SCOPE_AFTER_REMOVE, clientScopeModel);

        return result;
    }

    @Override
    public void removeClientScopes(RealmModel realm) {
        log.tracef("removeClients(%s)%s", realm, getShortStackTrace());
        ClientScopes clientScopesOfRealm = getScopes(realm.getId());

        repository.removeClientScopes(realm.getId());
        markDeleted(realm.getId());
    }

    @Override
    public ClientScopeModel getClientScopeById(RealmModel realm, String id) {
        if (id == null) {
            return null;
        }

        log.tracef("getClientScopeById(%s, %s)%s", realm, id, getShortStackTrace());
        return entityToAdapterFunc(realm).apply(getScopes(realm.getId()).getClientScopeById(id));
    }

    public void preRemove(RealmModel realm) {
        removeClientScopes(realm);
    }

    @Override
    public void close() {
        scopesByRealmId.clear();
        scopesChanged.clear();
        scopesDeleted.clear();
    }
}
