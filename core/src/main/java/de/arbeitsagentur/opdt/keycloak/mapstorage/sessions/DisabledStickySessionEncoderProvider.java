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

package de.arbeitsagentur.opdt.keycloak.mapstorage.sessions;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.sessions.StickySessionEncoderProvider;
import org.keycloak.sessions.StickySessionEncoderProviderFactory;

import static org.keycloak.userprofile.DeclarativeUserProfileProvider.PROVIDER_PRIORITY;

/**
 * Identical with "disabled"-provider from map storage days but without environment dependent activation
 */
@AutoService(StickySessionEncoderProviderFactory.class)
public class DisabledStickySessionEncoderProvider implements StickySessionEncoderProviderFactory, StickySessionEncoderProvider {

    @Override
    public StickySessionEncoderProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public String encodeSessionId(String sessionId) {
        return sessionId;
    }

    @Override
    public String decodeSessionId(String encodedSessionId) {
        return encodedSessionId;
    }

    @Override
    public boolean shouldAttachRoute() {
        return false;
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
        return "disabled";
    }

    @Override
    public int order() {
        return PROVIDER_PRIORITY + 1;
    }
}
