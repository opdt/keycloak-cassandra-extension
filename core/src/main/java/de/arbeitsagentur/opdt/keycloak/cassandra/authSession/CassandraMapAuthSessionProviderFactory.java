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
package de.arbeitsagentur.opdt.keycloak.cassandra.authSession;

import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.AbstractCassandraProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.CassandraAuthSessionProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraUserSessionProvider;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserSessionProviderFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.sessions.AuthenticationSessionProviderFactory;

// TODO: Remove as soon as DatastoreProvider covers auth sessions
@JBossLog
@AutoService(AuthenticationSessionProviderFactory.class)
public class CassandraMapAuthSessionProviderFactory extends AbstractCassandraProviderFactory implements AuthenticationSessionProviderFactory<CassandraAuthSessionProvider>, EnvironmentDependentProviderFactory {
  private static final String PROVIDER_ID = "map";

  public static final String AUTH_SESSIONS_LIMIT = "authSessionsLimit";

  public static final int DEFAULT_AUTH_SESSIONS_LIMIT = 300;

  private int authSessionsLimit;

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public CassandraAuthSessionProvider create(KeycloakSession session) {
    return new CassandraAuthSessionProvider(session, createRepository(session), authSessionsLimit);
  }

  @Override
  public void init(Config.Scope scope) {
    // get auth sessions limit from config or use default if not provided
    int configInt = scope.getInt(AUTH_SESSIONS_LIMIT, DEFAULT_AUTH_SESSIONS_LIMIT);
    // use default if provided value is not a positive number
    authSessionsLimit = (configInt <= 0) ? DEFAULT_AUTH_SESSIONS_LIMIT : configInt;

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

}
