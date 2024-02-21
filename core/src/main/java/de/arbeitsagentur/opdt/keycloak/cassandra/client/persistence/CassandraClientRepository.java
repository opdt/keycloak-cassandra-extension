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

import de.arbeitsagentur.opdt.keycloak.cassandra.client.CassandraClientAdapter;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.Client;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.ClientSearchIndex;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalRepository;
import java.util.List;

public class CassandraClientRepository extends TransactionalRepository<Client, ClientDao>
    implements ClientRepository {

  private static final String CLIENT_ID = "clientId";

  public CassandraClientRepository(ClientDao dao) {
    super(dao);
  }

  @Override
  public void insertOrUpdate(Client entity) {
    if (entity.getAttributes().containsKey(CassandraClientAdapter.CLIENT_ID)) {
      dao.insertOrUpdate(
          new ClientSearchIndex(
              entity.getRealmId(),
              CLIENT_ID,
              entity.getAttribute(CassandraClientAdapter.CLIENT_ID).get(0),
              entity.getId()));
    }

    super.insertOrUpdate(entity);
  }

  @Override
  public void delete(Client client) {
    if (client.getAttributes().containsKey(CassandraClientAdapter.CLIENT_ID)) {
      dao.deleteIndex(
          client.getRealmId(),
          CLIENT_ID,
          client.getAttribute(CassandraClientAdapter.CLIENT_ID).get(0),
          client.getId());
    }
    dao.delete(client);
  }

  @Override
  public Client getClientById(String realmId, String id) {
    return dao.getClientById(realmId, id);
  }

  public Client findByClientId(String realmId, String clientId) {
    ClientSearchIndex index = dao.findClient(realmId, CLIENT_ID, clientId);
    if (index == null) {
      return null;
    }

    return dao.getClientById(realmId, index.getClientId());
  }

  @Override
  public long countClientsByRealm(String realmId) {
    return dao.findAllClientsWithRealmId(realmId)
        .all()
        .size(); // isn't using count() for Amazon Keyspaces support
  }

  @Override
  public List<Client> findAllClientsWithRealmId(String realmId) {
    return dao.findAllClientsWithRealmId(realmId).all();
  }
}
