/*
 * Copyright 2024 IT-Systemhaus der Bundesagentur fuer Arbeit
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

package de.arbeitsagentur.opdt.keycloak.compatibility;

import static org.keycloak.userprofile.DeclarativeUserProfileProviderFactory.PROVIDER_PRIORITY;

import com.google.auto.service.AutoService;
import java.util.Map;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.dblock.DBLockProvider;
import org.keycloak.models.dblock.DBLockProviderFactory;
import org.keycloak.provider.ServerInfoAwareProviderFactory;

@AutoService(DBLockProviderFactory.class)
public class NullDbLockProviderFactory
    implements DBLockProviderFactory, DBLockProvider, ServerInfoAwareProviderFactory {
  private volatile Namespace lockedNamespace;

  @Override
  public void setTimeouts(long l, long l1) {}

  @Override
  public DBLockProvider create(KeycloakSession keycloakSession) {
    return this;
  }

  @Override
  public void init(Config.Scope scope) {}

  @Override
  public void postInit(KeycloakSessionFactory keycloakSessionFactory) {}

  @Override
  public void close() {}

  @Override
  public String getId() {
    return "jpa";
  }

  @Override
  public void waitForLock(Namespace namespace) {
    this.lockedNamespace = namespace;
  }

  @Override
  public void releaseLock() {
    lockedNamespace = null;
  }

  @Override
  public Namespace getCurrentLock() {
    return lockedNamespace;
  }

  @Override
  public boolean supportsForcedUnlock() {
    return false;
  }

  @Override
  public void destroyLockInfo() {}

  @Override
  public Map<String, String> getOperationalInfo() {
    return Map.of("implementation", "null (cassandra-extension)");
  }

  @Override
  public int order() {
    return PROVIDER_PRIORITY + 1;
  }
}
