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

import de.arbeitsagentur.opdt.keycloak.cassandra.CompositeRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.ClientRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.Client;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalProvider;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static de.arbeitsagentur.opdt.keycloak.mapstorage.common.MapProviderObjectType.CLIENT_AFTER_REMOVE;
import static de.arbeitsagentur.opdt.keycloak.mapstorage.common.MapProviderObjectType.CLIENT_BEFORE_REMOVE;

@JBossLog
public class CassandraClientProvider extends TransactionalProvider<Client, CassandraClientAdapter> implements ClientProvider {
    private final ClientRepository clientRepository;


    public CassandraClientProvider(KeycloakSession session, CompositeRepository cassandraRepository) {
        super(session);
        this.clientRepository = cassandraRepository;
    }

    @Override
    protected CassandraClientAdapter createNewModel(RealmModel realm, Client entity) {
        return new CassandraClientAdapter(entity, session, realm, clientRepository);
    }

    @Override
    public Stream<ClientModel> getClientsStream(RealmModel realm, Integer firstResult, Integer maxResults) {
        return Stream.concat(
                models.values().stream()
                    .filter(m -> m.getRealm().equals(realm)),
                clientRepository.findAllClientsWithRealmId(realm.getId()).stream()
                    .filter(Objects::nonNull)
                    .map(entityToAdapterFunc(realm))).distinct()
            .map(ClientModel.class::cast)
            .sorted(Comparator.comparing(ClientModel::getClientId))
            .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
            .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults);
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

        String newId = id == null ? KeycloakModelUtils.generateId() : id;
        Client client = new Client(realm.getId(), newId, null, new HashMap<>());
        clientRepository.insertOrUpdate(client);

        ClientModel adapter = entityToAdapterFunc(realm).apply(client);
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
        if (client == null) {
            return false;
        }

        ClientModel clientModel = getClientById(realm, id);
        session.invalidate(CLIENT_BEFORE_REMOVE, realm, clientModel);

        clientRepository.delete(client);
        ((CassandraClientAdapter) clientModel).markDeleted();
        models.remove(client.getId());

        session.invalidate(CLIENT_AFTER_REMOVE, clientModel);

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
            .filter(clientScope -> !existingClientScopes.containsKey(clientScope.getName()))
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
        return Stream.concat(models.values().stream()
                    .filter(m -> m.getRealm().equals(realm)),
                clientRepository.findAllClientsWithRealmId(realm.getId()).stream()
                    .map(entityToAdapterFunc(realm)))
            .distinct()
            .filter(ClientModel::isEnabled)
            .filter(c -> !c.getRedirectUris().isEmpty())
            .collect(Collectors.toMap(Function.identity(), ClientModel::getRedirectUris));
    }

    @Override
    public ClientModel getClientById(RealmModel realm, String id) {
        log.tracef("getClientById(%s, %s)%s", realm, id, getShortStackTrace());

        Client client = clientRepository.getClientById(realm.getId(), id);
        return entityToAdapterFunc(realm).apply(client);
    }

    @Override
    public ClientModel getClientByClientId(RealmModel realm, String clientId) {
        return Stream.concat(models.values().stream()
                    .filter(m -> m.getRealm().equals(realm)),
                clientRepository.findAllClientsWithRealmId(realm.getId()).stream()
                    .map(entityToAdapterFunc(realm)))
            .distinct()
            .filter(e -> Objects.equals(e.getClientId(), clientId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public Stream<ClientModel> searchClientsByClientIdStream(RealmModel realm, String clientId, Integer firstResult, Integer maxResults) {
        if (clientId == null) {
            return Stream.empty();
        }

        return Stream.concat(models.values().stream()
                    .filter(m -> m.getRealm().equals(realm)),
                clientRepository.findAllClientsWithRealmId(realm.getId()).stream()
                    .map(entityToAdapterFunc(realm)))
            .distinct()
            .map(ClientModel.class::cast)
            .filter(e -> "%".equals(clientId) || e.getAttribute(CassandraClientAdapter.CLIENT_ID).toLowerCase().contains(clientId.toLowerCase()))
            .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
            .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults);
    }

    @Override
    public Stream<ClientModel> searchClientsByAttributes(RealmModel realm, Map<String, String> attributes, Integer firstResult, Integer maxResults) {
        return Stream.concat(models.values().stream()
                    .filter(m -> m.getRealm().equals(realm)),
                clientRepository.findAllClientsWithRealmId(realm.getId()).stream()
                    .map(entityToAdapterFunc(realm)))
            .distinct()
            .map(ClientModel.class::cast)
            .filter(c -> attributes.isEmpty() || c.getAttributes().entrySet().containsAll(attributes.entrySet()))
            .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
            .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults);
    }

    @Override
    public Map<String, ClientScopeModel> getClientScopes(RealmModel realm, ClientModel client, boolean defaultScopes) {
        if (client == null) return null;

        // Defaults to openid-connect
        String clientProtocol = client.getProtocol() == null ? "openid-connect" : client.getProtocol();

        log.tracef("getClientScopes(%s, %s, %b)%s", realm, client, defaultScopes, getShortStackTrace());

        return client.getClientScopes(defaultScopes).values().stream()
            .filter(Objects::nonNull)
            .filter(clientScope -> Objects.equals(clientScope.getProtocol(), clientProtocol))
            .collect(Collectors.toMap(ClientScopeModel::getName, Function.identity()));
    }

    public void preRemove(RealmModel realm, RoleModel role) {
        realm.getClientsStream().forEach(c -> c.deleteScopeMapping(role));
    }

    public void preRemove(RealmModel realm) {
        this.removeClients(realm);
    }
}
