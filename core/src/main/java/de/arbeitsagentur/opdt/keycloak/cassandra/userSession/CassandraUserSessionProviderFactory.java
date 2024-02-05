/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.arbeitsagentur.opdt.keycloak.cassandra.userSession;

import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraCacheProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.ProviderHelpers.createProviderCached;
import static org.keycloak.userprofile.DeclarativeUserProfileProvider.PROVIDER_PRIORITY;

import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.connection.CassandraConnectionProvider;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.*;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

@JBossLog
@AutoService(UserSessionProviderFactory.class)
public class CassandraUserSessionProviderFactory
    implements UserSessionProviderFactory<CassandraUserSessionProvider>,
        EnvironmentDependentProviderFactory {

  @Override
  public CassandraUserSessionProvider create(KeycloakSession session) {
    CassandraConnectionProvider cassandraConnectionProvider =
        createProviderCached(session, CassandraConnectionProvider.class);
    return new CassandraUserSessionProvider(session, cassandraConnectionProvider.getRepository());
  }

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}

  @Override
  public String getId() {
    return "infinispan"; // use same name as infinispan provider to override it
  }

  @Override
  public int order() {
    return PROVIDER_PRIORITY + 1;
  }

  @Override
  public boolean isSupported() {
    return isCassandraProfileEnabled() || isCassandraCacheProfileEnabled();
  }

  @Override
  public void loadPersistentSessions(
      KeycloakSessionFactory sessionFactory, int maxErrors, int sessionsPerSegment) {}
}
