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
package de.arbeitsagentur.opdt.keycloak.cassandra.userSession;

import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.AbstractCassandraProviderFactory;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.*;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.InvalidationHandler;

import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.REALM_BEFORE_REMOVE;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.USER_BEFORE_REMOVE;

// TODO: Remove as soon as DatastoreProvider covers user sessions
@JBossLog
@AutoService(UserSessionProviderFactory.class)
public class CassandraMapUserSessionProviderFactory extends AbstractCassandraProviderFactory implements UserSessionProviderFactory<CassandraUserSessionProvider>, EnvironmentDependentProviderFactory, InvalidationHandler {
    private static final String PROVIDER_ID = "map";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public CassandraUserSessionProvider create(KeycloakSession session) {
        return new CassandraUserSessionProvider(session, createRepository(session));
    }

    @Override
    public void init(Config.Scope scope) {

    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
    }

    @Override
    public void close() {

    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public void loadPersistentSessions(KeycloakSessionFactory sessionFactory, int maxErrors, int sessionsPerSegment) {

    }

    @Override
    public void invalidate(KeycloakSession session, InvalidationHandler.InvalidableObjectType type, Object... params) {
        if (type == USER_BEFORE_REMOVE) {
            create(session).removeUserSessions((RealmModel) params[0], (UserModel) params[1]);
        } else if (type == REALM_BEFORE_REMOVE) {
            create(session).removeAllUserSessions((RealmModel) params[0]);
        }
    }
}
