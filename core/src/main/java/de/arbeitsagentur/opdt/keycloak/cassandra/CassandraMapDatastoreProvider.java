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
package de.arbeitsagentur.opdt.keycloak.cassandra;

import de.arbeitsagentur.opdt.keycloak.cassandra.client.CassandraClientProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.CassandraClientScopeProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.exportImportManager.CassandraExportImportManager;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.CassandraRealmsProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.CassandraRoleProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.CassandraUserProvider;
import org.keycloak.models.*;
import org.keycloak.models.map.datastore.MapDatastoreProvider;
import org.keycloak.storage.ExportImportManager;

public class CassandraMapDatastoreProvider extends MapDatastoreProvider {
    private KeycloakSession session;
    private CompositeRepository cassandraRepository;

    private RealmProvider realmProvider;
    private UserProvider userProvider;
    private RoleProvider roleProvider;
    private ClientProvider clientProvider;
    private ClientScopeProvider clientScopeProvider;
    private ExportImportManager exportImportManager;

    public CassandraMapDatastoreProvider(KeycloakSession session, CompositeRepository cassandraRepository) {
        super(session);
        this.session = session;
        this.cassandraRepository = cassandraRepository;
    }

    @Override
    public RealmProvider realms() {
        if (realmProvider == null) {
            realmProvider = new CassandraRealmsProvider(session, cassandraRepository);
        }
        return realmProvider;
    }

    @Override
    public UserProvider users() {
        if (userProvider == null) {
            userProvider = new CassandraUserProvider(session, cassandraRepository);
        }
        return userProvider;
    }

    @Override
    public RoleProvider roles() {
        if (roleProvider == null) {
            roleProvider = new CassandraRoleProvider(cassandraRepository);
        }
        return roleProvider;
    }

    @Override
    public ClientProvider clients() {
        if (clientProvider == null) {
            clientProvider = new CassandraClientProvider(session, cassandraRepository);
        }
        return clientProvider;
    }

    @Override
    public ClientScopeProvider clientScopes() {
        if (clientScopeProvider == null) {
            clientScopeProvider = new CassandraClientScopeProvider(session, cassandraRepository);
        }
        return clientScopeProvider;
    }

    @Override
    public ExportImportManager getExportImportManager() {
        if (exportImportManager == null) {
            exportImportManager = new CassandraExportImportManager(session);
        }
        return exportImportManager;
    }

}
