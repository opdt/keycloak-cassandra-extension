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
import de.arbeitsagentur.opdt.keycloak.cassandra.group.CassandraGroupProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.CassandraLoginFailureProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.CassandraRealmsProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.CassandraRoleProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.CassandraSingleUseObjectProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.CassandraUserProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraUserSessionProvider;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.*;
import org.keycloak.provider.Provider;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.storage.ExportImportManager;
import org.keycloak.storage.MigrationManager;
import org.keycloak.storage.datastore.LegacyDatastoreProvider;
import org.keycloak.storage.federated.UserFederatedStorageProvider;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

@JBossLog
public class CassandraDatastoreProvider extends LegacyDatastoreProvider {
    private final KeycloakSession session;

    private final Set<Provider> providersToClose = new HashSet<>();

    public CassandraDatastoreProvider(KeycloakSession session) {
        super(null, session);
        this.session = session;
    }

    @Override
    public RealmProvider realms() {
        return session.getProvider(RealmProvider.class);
    }

    @Override
    public UserProvider users() {
        return session.getProvider(UserProvider.class);
    }

    @Override
    public RoleProvider roles() {
        return session.getProvider(RoleProvider.class);
    }

    @Override
    public GroupProvider groups() {
        return session.getProvider(GroupProvider.class);
    }

    @Override
    public ClientProvider clients() {
        return session.getProvider(ClientProvider.class);
    }

    @Override
    public ClientScopeProvider clientScopes() {
        return session.getProvider(ClientScopeProvider.class);
    }

    @Override
    public SingleUseObjectProvider singleUseObjects() {
        return session.getProvider(SingleUseObjectProvider.class);
    }

    @Override
    public UserLoginFailureProvider loginFailures() {
        return session.getProvider(UserLoginFailureProvider.class);
    }

    @Override
    public AuthenticationSessionProvider authSessions() {
        return session.getProvider(AuthenticationSessionProvider.class);
    }

    @Override
    public UserSessionProvider userSessions() {
        return session.getProvider(UserSessionProvider.class);
    }

    @Override
    public ExportImportManager getExportImportManager() {
        return new CassandraLegacyExportImportManager(session);
    }

    @Override
    public UserProvider userLocalStorage() {
        return users();
    }

    @Override
    public ClientProvider clientStorageManager() {
        return clients();
    }

    @Override
    public ClientScopeProvider clientScopeStorageManager() {
        return clientScopes();
    }

    @Override
    public GroupProvider groupStorageManager() {
        return groups();
    }

    @Override
    public UserProvider userStorageManager() {
        return users();
    }

    @Override
    public RoleProvider roleStorageManager() {
        return roles();
    }

    @Override
    public MigrationManager getMigrationManager() {
        return new CassandraMigrationManager();
    }

    @Override
    public UserFederatedStorageProvider userFederatedStorage() {
        return null;
    }

}
