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


import javax.inject.Singleton;
import java.util.Map;
import java.util.WeakHashMap;

@Singleton
public class ThreadLocalCache {
  static final Object NONE = new Object();

  private static final ThreadLocal<Map<CacheInvocationContext, Object>> threadLocalCacheContainer = new ThreadLocal<>();

  public Object get(CacheInvocationContext invocationContext) {
    Map<CacheInvocationContext, Object> cache = getCache();

    return cache.getOrDefault(invocationContext, NONE);
  }

  void put(CacheInvocationContext methodInvocation, Object result) {
    Map<CacheInvocationContext, Object> cache = getCache();
    cache.put(methodInvocation, result);
  }

  public void reset() {
    threadLocalCacheContainer.remove();
  }

  private Map<CacheInvocationContext, Object> getCache() {
    Map<CacheInvocationContext, Object> cache = threadLocalCacheContainer.get();
    if(cache == null) {
      cache = new WeakHashMap<>();
      threadLocalCacheContainer.set(cache);
    }
    return cache;
  }
}
