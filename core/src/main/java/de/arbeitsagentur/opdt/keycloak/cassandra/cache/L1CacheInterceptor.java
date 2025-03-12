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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;

@JBossLog
@RequiredArgsConstructor
public class L1CacheInterceptor implements InvocationHandler {
    private static final Set<String> CACHE_INVALIDATION_NAMES = new HashSet<>();

    static {
        CACHE_INVALIDATION_NAMES.add("create");
        CACHE_INVALIDATION_NAMES.add("update");
        CACHE_INVALIDATION_NAMES.add("add");
        CACHE_INVALIDATION_NAMES.add("delete");
        CACHE_INVALIDATION_NAMES.add("remove");
        CACHE_INVALIDATION_NAMES.add("insert");
        CACHE_INVALIDATION_NAMES.add("make");
    }

    private final KeycloakSession session;
    private final Object target;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Method classMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
        L1Cached cacheAnnotation = classMethod.getAnnotation(L1Cached.class);

        if (cacheAnnotation == null) {
            return method.invoke(target, args);
        }

        String cacheName = cacheAnnotation.cacheName();
        boolean invalidateCache = classMethod.getAnnotation(InvalidateCache.class) != null;

        if (invalidateCache) {
            if (log.isTraceEnabled()) {
                log.tracef(
                        "Cache wird invalidiert durch Methode %s (%s)",
                        method.getName(),
                        Arrays.stream(args)
                                .map(Object::getClass)
                                .map(Object::toString)
                                .collect(Collectors.joining(", ")));
            }

            KeycloakSessionCache.reset(session, cacheName);

            return method.invoke(target, args);
        } else if (CACHE_INVALIDATION_NAMES.stream()
                .anyMatch(name -> method.getName().toLowerCase().contains(name))) {
            log.warnf(
                    "Method %s(%s) might need to invalidate cache but isnt annotated with @InvalidateCache",
                    method.getName(),
                    Arrays.stream(args)
                            .map(Object::getClass)
                            .map(Object::toString)
                            .collect(Collectors.joining(", ")));
        }

        CacheInvocationContext cacheInvocationContext = CacheInvocationContext.create(target, method, args);

        Object result = KeycloakSessionCache.get(session, cacheName, cacheInvocationContext);
        long timestamp = System.currentTimeMillis();

        if (KeycloakSessionCache.NONE == result) {
            result = method.invoke(target, args);

            if (log.isTraceEnabled()) {
                log.tracef(
                        "Uncached Call %s - %s",
                        cacheInvocationContext.getTargetMethod(), (System.currentTimeMillis() - timestamp) + "ms");
            }

            KeycloakSessionCache.put(session, cacheName, cacheInvocationContext, result);
        } else if (log.isTraceEnabled()) {
            log.tracef(
                    "Cached Result for Call %s - %s",
                    cacheInvocationContext.getTargetMethod(), (System.currentTimeMillis() - timestamp) + "ms");
        }

        return result;
    }
}
