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
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraUserSessionProvider;
import org.keycloak.models.*;
import org.keycloak.models.map.datastore.MapDatastoreProvider;
import org.keycloak.storage.ExportImportManager;

public class CassandraMapDatastoreProvider extends MapDatastoreProvider {
    private KeycloakSession session;
    private CompositeRepository cassandraRepository;

    public CassandraMapDatastoreProvider(KeycloakSession session, CompositeRepository cassandraRepository) {
        super(session);
        this.session = session;
        this.cassandraRepository = cassandraRepository;
    }

    @Override
    public RealmProvider realms() {
        return new CassandraRealmsProvider(session, cassandraRepository);
    }

    @Override
    public UserProvider users() {
        return session.getProvider(UserProvider.class, "map");
    }

    @Override
    public RoleProvider roles() {
        return new CassandraRoleProvider(cassandraRepository);
    }


    // TODO as soon as https://github.com/keycloak/keycloak/issues/15490 is implemented
    // @Override
    public UserSessionProvider userSessions() {
        return new CassandraUserSessionProvider(session, cassandraRepository);
    }

    @Override
    public ClientProvider clients() {
        return new CassandraClientProvider(session, cassandraRepository);
    }

    @Override
    public ClientScopeProvider clientScopes() {
        return new CassandraClientScopeProvider(session, cassandraRepository);
    }

    @Override
    public ExportImportManager getExportImportManager() {
        return new CassandraExportImportManager(session);
    }

}
