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
import org.keycloak.provider.Provider;
import org.keycloak.storage.ExportImportManager;

import java.io.Closeable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CassandraMapDatastoreProvider extends MapDatastoreProvider {
    private KeycloakSession session;
    private CompositeRepository cassandraRepository;

    private Set<Provider> providersToClose = new HashSet<>();

    public CassandraMapDatastoreProvider(KeycloakSession session, CompositeRepository cassandraRepository) {
        super(session);
        this.session = session;
        this.cassandraRepository = cassandraRepository;
    }

    @Override
    public RealmProvider realms() {
        return createProvider(RealmProvider.class, () -> new CassandraRealmsProvider(session, cassandraRepository));
    }

    @Override
    public UserProvider users() {
        return createProvider(UserProvider.class, () -> new CassandraUserProvider(session, cassandraRepository));
    }

    @Override
    public RoleProvider roles() {
        return createProvider(RoleProvider.class, () -> new CassandraRoleProvider(cassandraRepository));
    }

    @Override
    public ClientProvider clients() {
        return createProvider(ClientProvider.class, () -> new CassandraClientProvider(session, cassandraRepository));
    }

    @Override
    public ClientScopeProvider clientScopes() {
        return createProvider(ClientScopeProvider.class, () -> new CassandraClientScopeProvider(session, cassandraRepository));
    }

    @Override
    public ExportImportManager getExportImportManager() {
        return new CassandraExportImportManager(session);
    }

    private <T extends Provider> T createProvider(Class<T> providerClass, Supplier<T> providerSupplier) {
        T provider = session.getAttribute(providerClass.getName(), providerClass);
        if (provider != null) {
            return provider;
        }
        provider = providerSupplier.get();
        session.setAttribute(providerClass.getName(), provider);
        providersToClose.add(provider);

        return provider;
    }

    @Override
    public void close() {
        super.close();
        Consumer<Provider> safeClose = p -> {
            try {
                p.close();
            } catch (Exception e) {
                // Ignore exception
            }
        };
        providersToClose.forEach(safeClose);
    }
}
