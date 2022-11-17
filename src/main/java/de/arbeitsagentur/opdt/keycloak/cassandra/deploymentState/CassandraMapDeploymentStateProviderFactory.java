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
package de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState;

import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.AbstractCassandraProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.CassandraAuthSessionProvider;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.DeploymentStateProvider;
import org.keycloak.models.DeploymentStateProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.sessions.AuthenticationSessionProviderFactory;

// TODO: Remove as soon as DatastoreProvider covers deployment state
@JBossLog
@AutoService(DeploymentStateProviderFactory.class)
public class CassandraMapDeploymentStateProviderFactory extends AbstractCassandraProviderFactory implements DeploymentStateProviderFactory, EnvironmentDependentProviderFactory {
  private static final String PROVIDER_ID = "map";

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public DeploymentStateProvider create(KeycloakSession session) {
    return new CassandraDeploymentStateProvider(createRepository(session));
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

}
