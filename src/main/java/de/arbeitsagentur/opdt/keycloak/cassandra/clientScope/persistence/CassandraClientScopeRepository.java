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
package de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScope;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.NameToClientScope;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CassandraClientScopeRepository implements ClientScopeRepository {
    private final ClientScopeDao clientScopeDao;

    @Override
    public void insertOrUpdate(ClientScope clientScope) {
        clientScopeDao.insertOrUpdate(clientScope);
        clientScopeDao.insertOrUpdate(new NameToClientScope(clientScope.getName(), clientScope.getId()));
    }

    @Override
    public ClientScope getClientScopeById(String id) {
        return clientScopeDao.getClientScopeById(id);
    }

    @Override
    public List<ClientScope> findAllClientScopes() {
        return clientScopeDao.findAllClientScopes().all();
    }

    @Override
    public void remove(ClientScope clientScope) {
        clientScopeDao.delete(clientScope);
        clientScopeDao.deleteNameToClientScope(clientScope.getName());
    }

    @Override
    public ClientScope findClientScopeByName(String name) {
        NameToClientScope byName = clientScopeDao.findByName(name);
        if(byName == null) {
            return null;
        }

        return getClientScopeById(byName.getId());
    }
}
