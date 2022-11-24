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

import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopes;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CassandraClientScopeRepository implements ClientScopeRepository {
    private final ClientScopeDao clientScopeDao;

    @Override
    public void addOrUpdateClientScopes(ClientScopes clientScopes) {
        clientScopeDao.insertOrUpdate(clientScopes);
    }

    @Override
    public ClientScopes getClientScopesByRealmId(String realmId) {
        ClientScopes clientScopes = clientScopeDao.getClientScopesByRealmId(realmId);
        if(clientScopes == null) {
            clientScopes = ClientScopes.builder().realmId(realmId).build();
        }

        return clientScopes;
    }

    @Override
    public void removeClientScopes(String realmId) {
        clientScopeDao.deleteAllClientScopes(realmId);
    }
}
