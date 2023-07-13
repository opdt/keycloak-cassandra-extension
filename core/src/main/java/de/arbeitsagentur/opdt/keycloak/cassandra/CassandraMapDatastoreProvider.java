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

import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.CassandraAuthSessionProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.CassandraClientProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.CassandraClientScopeProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.exportImportManager.CassandraExportImportManager;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.CassandraGroupProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.CassandraLoginFailureProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.CassandraRealmsProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.CassandraRoleProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.CassandraSingleUseObjectProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.CassandraUserProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraUserSessionProvider;
import org.keycloak.Config;
import org.keycloak.models.*;
import org.keycloak.models.map.datastore.MapDatastoreProvider;
import org.keycloak.provider.Provider;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.storage.ExportImportManager;

import java.io.Closeable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CassandraMapDatastoreProvider extends MapDatastoreProvider {
    private final Config.Scope config;
    private final KeycloakSession session;
    private final CompositeRepository cassandraRepository;

    private final Set<Provider> providersToClose = new HashSet<>();

    public static final String AUTH_SESSIONS_LIMIT = "authSessionsLimit";
    public static final String CACHE_MODE = "cacheMode";

    public static final int DEFAULT_AUTH_SESSIONS_LIMIT = 300;

    private final int authSessionsLimit;
    private final boolean cacheMode;

    public CassandraMapDatastoreProvider(Config.Scope config, KeycloakSession session, CompositeRepository cassandraRepository) {
        super(session);
        this.config = config;
        this.session = session;
        this.cassandraRepository = cassandraRepository;

        // get auth sessions limit from config or use default if not provided
        int configInt = config.getInt(AUTH_SESSIONS_LIMIT, DEFAULT_AUTH_SESSIONS_LIMIT);
        // use default if provided value is not a positive number
        authSessionsLimit = (configInt <= 0) ? DEFAULT_AUTH_SESSIONS_LIMIT : configInt;

        this.cacheMode = config.getBoolean(CACHE_MODE, false);
    }

    @Override
    public RealmProvider realms() {
        return createProvider(RealmProvider.class, () -> new CassandraRealmsProvider(session, cassandraRepository), false);
    }

    @Override
    public UserProvider users() {
        return createProvider(UserProvider.class, () -> new CassandraUserProvider(session, cassandraRepository), false);
    }

    @Override
    public RoleProvider roles() {
        return createProvider(RoleProvider.class, () -> new CassandraRoleProvider(cassandraRepository, session), false);
    }

    @Override
    public GroupProvider groups() {
        return createProvider(GroupProvider.class, () -> new CassandraGroupProvider(cassandraRepository, session), false);
    }

    @Override
    public ClientProvider clients() {
        return createProvider(ClientProvider.class, () -> new CassandraClientProvider(session, cassandraRepository), false);
    }

    @Override
    public ClientScopeProvider clientScopes() {
        return createProvider(ClientScopeProvider.class, () -> new CassandraClientScopeProvider(session, cassandraRepository), false);
    }

    @Override
    public SingleUseObjectProvider singleUseObjects() {
        return createProvider(SingleUseObjectProvider.class, () -> new CassandraSingleUseObjectProvider(cassandraRepository), true);
    }

    @Override
    public UserLoginFailureProvider loginFailures() {
        return createProvider(UserLoginFailureProvider.class, () -> new CassandraLoginFailureProvider(cassandraRepository), true);
    }

    @Override
    public AuthenticationSessionProvider authSessions() {
        return createProvider(AuthenticationSessionProvider.class, () -> new CassandraAuthSessionProvider(session, cassandraRepository, authSessionsLimit), true);
    }

    @Override
    public UserSessionProvider userSessions() {
        return createProvider(UserSessionProvider.class, () -> new CassandraUserSessionProvider(session, cassandraRepository), true);
    }

    @Override
    public ExportImportManager getExportImportManager() {
        return new CassandraExportImportManager(session);
    }

    private <T extends Provider> T createProvider(Class<T> providerClass, Supplier<T> providerSupplier, boolean isCacheProvider) {
        T provider = session.getAttribute(providerClass.getName(), providerClass);
        if (provider != null) {
            return provider;
        }

        String providerConfigName = providerClass.getSimpleName()
            .toLowerCase()
            .replace("provider", "");
        Boolean providerEnabled = config.getBoolean(providerConfigName + "-enabled", true);
        if (!providerEnabled || (cacheMode && !isCacheProvider)) {
            T explicitlyConfiguredProvider = session.getProvider(providerClass);
            session.setAttribute(providerClass.getName(), explicitlyConfiguredProvider);
            return explicitlyConfiguredProvider;
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
