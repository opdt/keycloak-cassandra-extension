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
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.Objects;
import java.util.stream.Stream;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.CLIENT_SCOPE_AFTER_REMOVE;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.CLIENT_SCOPE_BEFORE_REMOVE;

@JBossLog
@RequiredArgsConstructor
public class CassandraClientScopeProvider implements ClientScopeProvider {
    private final KeycloakSession session;

    private final ClientScopeRepository repository;

    @Override
    public Stream<ClientScopeModel> getClientScopesStream(RealmModel realm) {
        return repository.findAllClientScopes().stream()
                .filter(e -> Objects.equals(realm.getId(), e.getRealmId()))
                .map(this::entityToModel);
    }

    private CassandraClientScopeAdapter entityToModel(ClientScope clientScope) {
        return clientScope == null ? null : new CassandraClientScopeAdapter(session, clientScope, repository);
    }

    @Override
    public ClientScopeModel addClientScope(RealmModel realm, String id, String name) {
        if (getClientScopeById(realm, id) != null) {
            throw new ModelDuplicateException("Client scope exists: " + id);
        }

        ClientScope existingClientScopeWithNameAndRealm = repository.findAllClientScopesByAttribute(CassandraClientScopeAdapter.NAME, name).stream()
                .filter(s -> Objects.equals(realm.getId(), s.getRealmId()))
                .findFirst()
                .orElse(null);
        if (existingClientScopeWithNameAndRealm != null) {
            throw new ModelDuplicateException("Client scope with name '" + name + "' in realm " + realm.getName());
        }

        log.tracef("addClientScope(%s, %s, %s)%s", realm, id, name, getShortStackTrace());

        ClientScope clientScope = ClientScope.builder()
                .id(id == null ? KeycloakModelUtils.generateId() : id)
                .realmId(realm.getId())
                .build();
        repository.create(clientScope);

        ClientScopeModel clientScopeModel = entityToModel(clientScope);
        clientScopeModel.setName(name);

        return clientScopeModel;
    }

    @Override
    public boolean removeClientScope(RealmModel realm, String id) {
        if (id == null) return false;
        ClientScope clientScope = repository.getClientScopeById(id);
        if (clientScope == null) return false;

        session.invalidate(CLIENT_SCOPE_BEFORE_REMOVE, realm, clientScope);

        repository.remove(clientScope);

        session.invalidate(CLIENT_SCOPE_AFTER_REMOVE, clientScope);

        return true;
    }

    @Override
    public void removeClientScopes(RealmModel realm) {
        log.tracef("removeClients(%s)%s", realm, getShortStackTrace());
        repository.findAllClientScopes().stream()
                .filter(s -> Objects.equals(realm.getId(), s.getRealmId()))
                .forEach(s -> repository.remove(s));
    }

    @Override
    public void close() {

    }

    @Override
    public ClientScopeModel getClientScopeById(RealmModel realm, String id) {
        return entityToModel(repository.getClientScopeById(id));
    }
}
