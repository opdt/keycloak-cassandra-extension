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
package de.arbeitsagentur.opdt.keycloak.cassandra.cache;


import de.arbeitsagentur.opdt.keycloak.cassandra.AttributeTypes;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

@JBossLog
public class KeycloakSessionCache {
    private static final String SESSION_CACHE_ATTRIBUTE = AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX + "sessionCache";
    public static final String USER_CACHE = "userCache";
    public static final String USER_CONSENT_CACHE = "userConsentCache";
    public static final String ROLE_CACHE = "roleCache";
    public static final String REALM_CACHE = "realmCache";
    public static final String USER_SESSION_CACHE = "userSessionCache";
    public static final String AUTH_SESSION_CACHE = "authSessionCache";
    public static final String LOGIN_FAILURE_CACHE = "loginFailureCache";
    public static final String SUO_CACHE = "suoCache";
    public static final String CLIENT_CACHE = "clientCache";
    public static final String CLIENT_SCOPE_CACHE = "clientScopeCache";

    static final Object NONE = new Object();


    public static Object get(KeycloakSession session, String cacheName, CacheInvocationContext invocationContext) {
        Map<CacheInvocationContext, Object> cache = getCache(session, cacheName);

        return cache.getOrDefault(invocationContext, NONE);
    }

    static void put(KeycloakSession session, String cacheName, CacheInvocationContext methodInvocation, Object result) {
        Map<CacheInvocationContext, Object> cache = getCache(session, cacheName);
        cache.put(methodInvocation, result);
    }

    public static void reset(KeycloakSession session, String cacheName) {
        log.tracef("Reset cache %s", cacheName);
        Map<String, Map<CacheInvocationContext, Object>> cache = session.getAttributeOrDefault(SESSION_CACHE_ATTRIBUTE, new HashMap<>());
        cache.remove(cacheName);
    }

    private static Map<CacheInvocationContext, Object> getCache(KeycloakSession session, String cacheName) {
        Map<String, Map<CacheInvocationContext, Object>> cache = session.getAttributeOrDefault(SESSION_CACHE_ATTRIBUTE, new HashMap<>());
        if (cache == null) {
            cache = new WeakHashMap<>();
            session.setAttribute(SESSION_CACHE_ATTRIBUTE, cache);
        }

        if (!cache.containsKey(cacheName)) {
            cache.put(cacheName, new WeakHashMap<>());
        }

        return cache.get(cacheName);
    }
}
