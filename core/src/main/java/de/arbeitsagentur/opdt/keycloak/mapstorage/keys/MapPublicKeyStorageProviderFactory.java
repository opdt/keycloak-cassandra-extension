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

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.crypto.PublicKeysWrapper;
import org.keycloak.keys.PublicKeyStorageProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

@AutoService(PublicKeyStorageProviderFactory.class)
public class MapPublicKeyStorageProviderFactory implements PublicKeyStorageProviderFactory<MapPublicKeyStorageProvider> {

    private final Map<String, FutureTask<PublicKeysWrapper>> tasksInProgress = new ConcurrentHashMap<>();

    @Override
    public MapPublicKeyStorageProvider create(KeycloakSession session) {
        return  new MapPublicKeyStorageProvider(session, tasksInProgress);
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
        return "map";
    }
}
