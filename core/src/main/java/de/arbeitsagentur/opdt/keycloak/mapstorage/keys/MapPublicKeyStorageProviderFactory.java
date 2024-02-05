/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.arbeitsagentur.opdt.keycloak.mapstorage.keys;

import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraCacheProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.ProviderHelpers.createProviderCached;
import static org.keycloak.userprofile.DeclarativeUserProfileProvider.PROVIDER_PRIORITY;

import com.google.auto.service.AutoService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import org.keycloak.Config;
import org.keycloak.crypto.PublicKeysWrapper;
import org.keycloak.keys.PublicKeyStorageProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

@AutoService(PublicKeyStorageProviderFactory.class)
public class MapPublicKeyStorageProviderFactory
    implements PublicKeyStorageProviderFactory<MapPublicKeyStorageProvider>,
        EnvironmentDependentProviderFactory {

  private final Map<String, FutureTask<PublicKeysWrapper>> tasksInProgress =
      new ConcurrentHashMap<>();

  @Override
  public MapPublicKeyStorageProvider create(KeycloakSession session) {
    return createProviderCached(
        session,
        MapPublicKeyStorageProvider.class,
        () -> new MapPublicKeyStorageProvider(session, tasksInProgress));
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
}
