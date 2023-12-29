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

package de.arbeitsagentur.opdt.keycloak.common;

import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.Provider;

import java.util.function.Supplier;

public class ProviderHelpers {
    public static <T extends Provider> T createProviderCached(KeycloakSession session, Class<T> providerClass) {
        return createProviderCached(session, providerClass, () -> session.getProvider(providerClass));
    }

    public static <T extends Provider> T createProviderCached(KeycloakSession session, Class<T> providerClass, Supplier<T> providerSupplier) {
        T provider = session.getAttribute(providerClass.getName(), providerClass);
        if (provider != null) {
            return provider;
        }

        provider = providerSupplier.get();
        session.setAttribute(providerClass.getName(), provider);

        return provider;
    }
}
