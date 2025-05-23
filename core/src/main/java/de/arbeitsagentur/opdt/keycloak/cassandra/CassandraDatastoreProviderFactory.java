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

import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraCacheProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.MapProviderObjectType.*;
import static de.arbeitsagentur.opdt.keycloak.common.ProviderHelpers.createProviderCached;
import static org.keycloak.userprofile.DeclarativeUserProfileProviderFactory.PROVIDER_PRIORITY;

import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.CassandraClientProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.CassandraClientScopeProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.CassandraGroupProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.CassandraRoleProvider;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.*;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.InvalidationHandler;
import org.keycloak.storage.DatastoreProvider;
import org.keycloak.storage.DatastoreProviderFactory;

@JBossLog
@AutoService(DatastoreProviderFactory.class)
public class CassandraDatastoreProviderFactory
        implements DatastoreProviderFactory, InvalidationHandler, EnvironmentDependentProviderFactory {
    private static final String PROVIDER_ID =
            "legacy"; // Override legacy provider to disable timers / event listeners and stuff...

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public DatastoreProvider create(KeycloakSession session) {
        return createProviderCached(session, DatastoreProvider.class, () -> new CassandraDatastoreProvider(session));
    }

    @Override
    public void init(Config.Scope scope) {
        log.info("Using cassandra datastore...");
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {}

    @Override
    public void close() {}

    @Override
    public void invalidate(KeycloakSession session, InvalidableObjectType type, Object... params) {
        if (type == REALM_BEFORE_REMOVE) {
            create(session).users().preRemove((RealmModel) params[0]);
            ((CassandraClientProvider) create(session).clients()).preRemove((RealmModel) params[0]);
            ((CassandraClientScopeProvider) create(session).clientScopes()).preRemove((RealmModel) params[0]);
            ((CassandraRoleProvider) create(session).roles()).preRemove((RealmModel) params[0]);
            ((CassandraGroupProvider) create(session).groups()).preRemove((RealmModel) params[0]);
        } else if (type == ROLE_BEFORE_REMOVE) {
            create(session).users().preRemove((RealmModel) params[0], (RoleModel) params[1]);
            ((CassandraClientProvider) create(session).clients())
                    .preRemove((RealmModel) params[0], (RoleModel) params[1]);
            ((CassandraRoleProvider) create(session).roles()).preRemove((RealmModel) params[0], (RoleModel) params[1]);
            ((CassandraGroupProvider) create(session).groups())
                    .preRemove((RealmModel) params[0], (RoleModel) params[1]);
        } else if (type == CLIENT_SCOPE_BEFORE_REMOVE) {
            create(session).users().preRemove((ClientScopeModel) params[1]);
            ((RealmModel) params[0]).removeDefaultClientScope((ClientScopeModel) params[1]);
        } else if (type == CLIENT_BEFORE_REMOVE) {
            create(session).users().preRemove((RealmModel) params[0], (ClientModel) params[1]);
            create(session).roles().removeRoles((ClientModel) params[1]);
        } else if (type == GROUP_BEFORE_REMOVE) {
            create(session).users().preRemove((RealmModel) params[0], (GroupModel) params[1]);
        } else if (type == CLIENT_AFTER_REMOVE) {
            session.getKeycloakSessionFactory().publish(new ClientModel.ClientRemovedEvent() {
                @Override
                public ClientModel getClient() {
                    return (ClientModel) params[0];
                }

                @Override
                public KeycloakSession getKeycloakSession() {
                    return session;
                }
            });
        } else if (type == CLIENT_SCOPE_AFTER_REMOVE) {
            session.getKeycloakSessionFactory().publish(new ClientScopeModel.ClientScopeRemovedEvent() {
                @Override
                public ClientScopeModel getClientScope() {
                    return (ClientScopeModel) params[0];
                }

                @Override
                public KeycloakSession getKeycloakSession() {
                    return session;
                }
            });
        } else if (type == ROLE_AFTER_REMOVE) {
            session.getKeycloakSessionFactory().publish(new RoleContainerModel.RoleRemovedEvent() {
                @Override
                public RoleModel getRole() {
                    return (RoleModel) params[1];
                }

                @Override
                public KeycloakSession getKeycloakSession() {
                    return session;
                }
            });
        }
    }

    @Override
    public int order() {
        return PROVIDER_PRIORITY + 1;
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return isCassandraProfileEnabled() || isCassandraCacheProfileEnabled();
    }
}
