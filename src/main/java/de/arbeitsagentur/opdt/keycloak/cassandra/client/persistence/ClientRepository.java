package de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.Client;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.ClientToAttributeMapping;

import java.util.List;
import java.util.stream.Stream;

public interface ClientRepository {

    void create(Client client);

    void delete(Client client);

    Client getClientById(String realmId, String id);

    long countClientsByRealm(String realmId);

    List<Client> findAllClientsWithRealmId(String realmId);

    List<ClientToAttributeMapping> findAllClientAttributes(String clientId);

    ClientToAttributeMapping findClientAttribute(String clientId, String attributeName);

    void insertOrUpdate(ClientToAttributeMapping mapping);

    void deleteClientAttribute(String clientId, String attributeName);
}
