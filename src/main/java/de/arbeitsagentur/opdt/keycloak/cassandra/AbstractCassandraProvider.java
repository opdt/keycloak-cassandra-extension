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

package de.arbeitsagentur.opdt.keycloak.cassandra;

import de.arbeitsagentur.opdt.keycloak.cassandra.cache.ThreadLocalCache;
import io.quarkus.arc.Arc;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.provider.Provider;

@JBossLog
public abstract class AbstractCassandraProvider implements Provider {
  @Override
  public void close() {
    log.debugf("Close provider %s", getClass().getName());
    ThreadLocalCache threadLocalCache = Arc.container().instance(ThreadLocalCache.class).get();
    threadLocalCache.reset(getCacheName());
  }

  protected abstract String getCacheName();
}
