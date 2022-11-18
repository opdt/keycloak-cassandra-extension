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
package de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.AttributeToClientMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.Client;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.ClientToAttributeMapping;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CassandraClientRepository implements ClientRepository {
    private final ClientDao clientDao;

    @Override
    public void create(Client client) {
        clientDao.insert(client);
    }

    @Override
    public void delete(Client client) {
        clientDao.delete(client);
    }

    @Override
    public Client getClientById(String realmId, String id) { return clientDao.getClientById(realmId, id); }

    @Override
    public long countClientsByRealm(String realmId) { return clientDao.count(); }

    @Override
    public List<Client> findAllClientsWithRealmId(String realmId) {
        return clientDao.findAllClientsWithRealmId(realmId).all();
    }

    @Override
    public List<ClientToAttributeMapping> findAllClientAttributes(String clientId) {
        return clientDao.findAllAttributes(clientId).all();
    }

    @Override
    public ClientToAttributeMapping findClientAttribute(String clientId, String attributeName) {
        return clientDao.findAttribute(clientId, attributeName);
    }

    @Override
    public void insertOrUpdate(ClientToAttributeMapping attributeMapping) {
        ClientToAttributeMapping oldAttribute = clientDao.findAttribute(attributeMapping.getClientId(), attributeMapping.getAttributeName());
        clientDao.insertOrUpdate(attributeMapping);

        if (oldAttribute != null) {
            // Alte AttributeToUserMappings löschen, da die Values als Teil des PartitionKey nicht
            // geändert werden können
            oldAttribute
                    .getAttributeValues()
                    .forEach(value -> clientDao.deleteAttributeToClientMapping(new AttributeToClientMapping(oldAttribute.getAttributeName(), value, oldAttribute.getClientId())));
        }

        attributeMapping
                .getAttributeValues()
                .forEach(value -> {
                    AttributeToClientMapping attributeToClientMapping = new AttributeToClientMapping(attributeMapping.getAttributeName(), value, attributeMapping.getClientId());
                    clientDao.insert(attributeToClientMapping);
                });
    }

    @Override
    public void deleteClientAttribute(String clientId, String attributeName) {
        ClientToAttributeMapping attribute = findClientAttribute(clientId, attributeName);

        if (attribute == null) {
            return;
        }

        // Beide Mapping-Tabellen beachten!
        clientDao.deleteAttribute(clientId, attributeName);
        attribute
                .getAttributeValues()
                .forEach(value -> clientDao.deleteAttributeToClientMapping(new AttributeToClientMapping(attributeName, value, clientId)));

    }
}
