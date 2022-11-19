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

import io.quarkus.arc.Priority;
import io.quarkus.arc.Unremovable;
import lombok.extern.jbosslog.JBossLog;

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Unremovable
@JBossLog
@L1Cached
@Priority(10)
@Interceptor
public class L1CacheInterceptor {
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

  private final ThreadLocalCache cache;

  @Inject
  public L1CacheInterceptor(ThreadLocalCache cache) {
    this.cache = cache;
  }

  @AroundInvoke
  Object invoke(InvocationContext context) throws Exception {
    L1Cached cacheAnnotation = context.getMethod().getAnnotation(L1Cached.class);
    String cacheName = cacheAnnotation.cacheName();
    boolean invalidateCache = context.getMethod().getAnnotation(InvalidateCache.class) != null;

    if(invalidateCache) {
      if(log.isTraceEnabled()) {
        log.tracef("Cache wird invalidiert durch Methode %s (%s)", context.getMethod().getName(),
            Arrays.stream(context.getParameters())
                .map(Object::getClass)
                .map(Object::toString)
                .collect(Collectors.joining(", ")));
      }

      cache.reset(cacheName);

      return context.proceed();
    } else if(CACHE_INVALIDATION_NAMES.stream().anyMatch(name -> context.getMethod().getName().toLowerCase().contains(name))) {
      log.warnf("Method %s(%s) might need to invalidate cache but isnt annotated with @InvalidateCache",
          context.getMethod().getName(), Arrays.stream(context.getParameters())
              .map(Object::getClass)
              .map(Object::toString)
              .collect(Collectors.joining(", ")));
    }

    CacheInvocationContext cacheInvocationContext = CacheInvocationContext.create(context);

    Object result = cache.get(cacheName, cacheInvocationContext);
    if (ThreadLocalCache.NONE == result) {
      long timestamp = System.currentTimeMillis();
      result = context.proceed();

      if(log.isTraceEnabled()) {
        log.tracef("Uncached Call %s - %s", cacheInvocationContext.getTargetMethod(), (System.currentTimeMillis() - timestamp) + "ms");
      }

      cache.put(cacheName, cacheInvocationContext, result);
    }

    return result;
  }
}
