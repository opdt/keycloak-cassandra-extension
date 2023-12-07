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

package de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.SingleUseObjectProviderFactory;

import static org.keycloak.userprofile.DeclarativeUserProfileProvider.PROVIDER_PRIORITY;

@AutoService(SingleUseObjectProviderFactory.class)
public class CassandraSingleUseObjectProviderFactory implements SingleUseObjectProviderFactory<CassandraSingleUseObjectProvider> {
    @Override
    public CassandraSingleUseObjectProvider create(KeycloakSession session) {
        return (CassandraSingleUseObjectProvider) session.getProvider(SingleUseObjectProvider.class);
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
        return "cassandra";
    }

    @Override
    public int order() {
        return PROVIDER_PRIORITY + 1;
    }
}
