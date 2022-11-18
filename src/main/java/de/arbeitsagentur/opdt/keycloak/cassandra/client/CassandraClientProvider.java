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
package de.arbeitsagentur.opdt.keycloak.cassandra.client;

import de.arbeitsagentur.opdt.keycloak.cassandra.ManagedCompositeCassandraRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.ClientRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.Client;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.models.map.client.MapClientEntity;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.CLIENT_AFTER_REMOVE;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.CLIENT_BEFORE_REMOVE;

@JBossLog
public class CassandraClientProvider implements ClientProvider {
    private final KeycloakSession session;

    private final ClientRepository clientRepository;


    public CassandraClientProvider(KeycloakSession session, ManagedCompositeCassandraRepository cassandraRepository) {
        this.session = session;
        this.clientRepository = cassandraRepository;
    }

    private ClientModel entityToAdapter(Client entity) {
        return entity == null ? null : new CassandraClientAdapter(session, entity, clientRepository);
    }

    @Override
    public Stream<ClientModel> getClientsStream(RealmModel realm, Integer firstResult, Integer maxResults) {
        return clientRepository.findAllClientsWithRealmId(realm.getId()).stream()
                .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
                .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
                .map(this::entityToAdapter)
                .sorted(Comparator.comparing(ClientModel::getName));
    }

    @Override
    public ClientModel addClient(RealmModel realm, String id, String clientId) {
        log.tracef("addClient(%s, %s, %s)%s", realm, id, clientId, getShortStackTrace());

        if (id != null && getClientById(realm, id) != null) {
            throw new ModelDuplicateException("Client with same id exists: " + id);
        }
        if (clientId != null && getClientByClientId(realm, clientId) != null) {
            throw new ModelDuplicateException("Client with same clientId in realm " + realm.getName() + " exists: " + clientId);
        }

        Client client = Client.builder()
                .id(id == null ? KeycloakModelUtils.generateId() : id)
                .realmId(realm.getId())
                .build();

        clientRepository.create(client);
        ClientModel adapter = entityToAdapter(client);
        adapter.setClientId(clientId != null ? clientId : client.getId());
        adapter.setEnabled(true);
        adapter.setStandardFlowEnabled(true);

        // TODO: Sending an event should be extracted to store layer
        session.getKeycloakSessionFactory().publish((ClientModel.ClientCreationEvent) () -> adapter);
        adapter.updateClient();        // This is actualy strange contract - it should be the store code to call updateClient

        return adapter;
    }

    @Override
    public long getClientsCount(RealmModel realm) {
        return clientRepository.countClientsByRealm(realm.getId());
    }

    @Override
    public Stream<ClientModel> getAlwaysDisplayInConsoleClientsStream(RealmModel realm) {
        return getClientsStream(realm)
                .filter(ClientModel::isAlwaysDisplayInConsole);
    }

    @Override
    public boolean removeClient(RealmModel realm, String id) {
        Client client = clientRepository.getClientById(realm.getId(), id);
        if(client == null) {
            return false;
        }

        session.invalidate(CLIENT_BEFORE_REMOVE, realm, client);

        clientRepository.delete(client);

        session.invalidate(CLIENT_AFTER_REMOVE, client);

        return true;
    }

    @Override
    public void removeClients(RealmModel realm) {
        log.tracef("removeClients(%s)%s", realm, getShortStackTrace());

        getClientsStream(realm)
                .map(ClientModel::getId)
                .forEach(cid -> removeClient(realm, cid));
    }

    @Override
    public void addClientScopes(RealmModel realm, ClientModel client, Set<ClientScopeModel> clientScopes, boolean defaultScope) {
        // Defaults to openid-connect
        String clientProtocol = client.getProtocol() == null ? "openid-connect" : client.getProtocol();

        log.tracef("addClientScopes(%s, %s, %s, %b)%s", realm, client, clientScopes, defaultScope, getShortStackTrace());

        Map<String, ClientScopeModel> existingClientScopes = getClientScopes(realm, client, true);
        existingClientScopes.putAll(getClientScopes(realm, client, false));

        clientScopes.stream()
                .filter(clientScope -> ! existingClientScopes.containsKey(clientScope.getName()))
                .filter(clientScope -> Objects.equals(clientScope.getProtocol(), clientProtocol))
                .forEach(clientScope -> client.addClientScope(clientScope, defaultScope));
    }

    @Override
    public void removeClientScope(RealmModel realm, ClientModel client, ClientScopeModel clientScope) {
        log.tracef("removeClientScope(%s, %s, %s)%s", realm, client, clientScope, getShortStackTrace());

        client.removeClientScope(clientScope);
    }

    @Override
    public Map<ClientModel, Set<String>> getAllRedirectUrisOfEnabledClients(RealmModel realm) {
        return clientRepository.findAllClientsWithRealmId(realm.getId()).stream()
                .map(this::entityToAdapter)
                .filter(ClientModel::isEnabled)
                .collect(Collectors.toMap(Function.identity(), ClientModel::getRedirectUris));
    }

    @Override
    public void close() {

    }

    @Override
    public ClientModel getClientById(RealmModel realm, String id) {
        log.tracef("getClientById(%s, %s)%s", realm, id, getShortStackTrace());

        Client client = clientRepository.getClientById(realm.getId(), id);
        return client == null ? null : entityToAdapter(client);
    }

    @Override
    public ClientModel getClientByClientId(RealmModel realm, String clientId) {
        return clientRepository.findAllClientsWithRealmId(realm.getId()).stream()
                .map(this::entityToAdapter)
                .filter(e -> Objects.equals(e.getClientId(), clientId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Stream<ClientModel> searchClientsByClientIdStream(RealmModel realm, String clientId, Integer firstResult, Integer maxResults) {
        return clientRepository.findAllClientsWithRealmId(realm.getId()).stream()
                .map(this::entityToAdapter)
                .filter(e -> Objects.equals(e.getClientId(), clientId))
                .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
                .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults);
    }

    @Override
    public Stream<ClientModel> searchClientsByAttributes(RealmModel realm, Map<String, String> attributes, Integer firstResult, Integer maxResults) {
        return clientRepository.findAllClientsWithRealmId(realm.getId()).stream()
                .map(this::entityToAdapter)
                .filter(c -> c.getAttributes().entrySet().containsAll(attributes.entrySet()))
                .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
                .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults);
    }

    @Override
    public Map<String, ClientScopeModel> getClientScopes(RealmModel realm, ClientModel client, boolean defaultScopes) {
        // TODO: what for is realm used?
        if (client == null) return null;

        return client.getClientScopes(defaultScopes);
    }
}
