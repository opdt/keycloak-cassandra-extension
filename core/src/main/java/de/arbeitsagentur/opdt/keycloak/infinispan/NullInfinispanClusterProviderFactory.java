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

package de.arbeitsagentur.opdt.keycloak.infinispan;

import com.google.auto.service.AutoService;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.cluster.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraCacheProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraProfileEnabled;
import static org.keycloak.userprofile.DeclarativeUserProfileProvider.PROVIDER_PRIORITY;

@JBossLog
@AutoService(ClusterProviderFactory.class)
public class NullInfinispanClusterProviderFactory implements ClusterProviderFactory, EnvironmentDependentProviderFactory {
    @Override
    public boolean isSupported() {
        return isCassandraProfileEnabled() || isCassandraCacheProfileEnabled();
    }

    @Override
    public ClusterProvider create(KeycloakSession session) {
        return new ClusterProvider() {
            @Override
            public int getClusterStartupTime() {
                return 0;
            }

            @Override
            public <T> ExecutionResult<T> executeIfNotExecuted(String taskKey, int taskTimeoutInSeconds, Callable<T> task) {
                return null;
            }

            @Override
            public Future<Boolean> executeIfNotExecutedAsync(String taskKey, int taskTimeoutInSeconds, Callable task) {
                return null;
            }

            @Override
            public void registerListener(String taskKey, ClusterListener task) {

            }

            @Override
            public void notify(String taskKey, ClusterEvent event, boolean ignoreSender, DCNotify dcNotify) {

            }

            @Override
            public void close() {

            }
        };
    }

    @Override
    public void init(Config.Scope config) {
        log.info("Infinispan-ClusterProvider deactivated...");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public int order() {
        return PROVIDER_PRIORITY + 1;
    }

    @Override
    public String getId() {
        return "infinispan";
    }
}
