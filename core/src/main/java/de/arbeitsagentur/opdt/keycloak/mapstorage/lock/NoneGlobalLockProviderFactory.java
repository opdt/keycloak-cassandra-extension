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
package de.arbeitsagentur.opdt.keycloak.mapstorage.lock;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTaskWithResult;
import org.keycloak.models.locking.GlobalLockProvider;
import org.keycloak.models.locking.GlobalLockProviderFactory;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

import java.time.Duration;

import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraCacheProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.ProviderHelpers.createProviderCached;
import static org.keycloak.userprofile.DeclarativeUserProfileProvider.PROVIDER_PRIORITY;

/**
 * Identical with "none"-global lock provider from map storage days but without environment dependent activation
 */
@AutoService(GlobalLockProviderFactory.class)
public class NoneGlobalLockProviderFactory implements GlobalLockProviderFactory, EnvironmentDependentProviderFactory {

    public static final String PROVIDER_ID = "dblock";

    @Override
    public GlobalLockProvider create(KeycloakSession session) {
        return createProviderCached(session, GlobalLockProvider.class, () -> new GlobalLockProvider() {
            @Override
            public void close() {
            }

            @Override
            public <V> V withLock(String lockName, Duration timeToWaitForLock, KeycloakSessionTaskWithResult<V> task) {
                return KeycloakModelUtils.runJobInTransactionWithResult(session.getKeycloakSessionFactory(), task);
            }

            @Override
            public void forceReleaseAllLocks() {

            }
        });
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return PROVIDER_ID;
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
