/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
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

package de.arbeitsagentur.opdt.keycloak.cassandra.transaction;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.Provider;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@JBossLog
public abstract class TransactionalProvider<TEntity extends TransactionalEntity, TModel extends TransactionalModelAdapter> implements Provider {

    protected final KeycloakSession session;
    protected final Map<String, TModel> models = new ConcurrentHashMap<>();

    public TransactionalProvider(KeycloakSession session) {
        this.session = session;
    }

    protected abstract TModel createNewModel(RealmModel realm, TEntity entity);

    protected Function<TEntity, TModel> entityToAdapterFunc(RealmModel realm) {
        return origEntity -> {
            if (origEntity == null) {
                return null;
            }

            TModel existingModel = models.get(origEntity.getId());
            if (existingModel != null) {
                log.tracef("Return cached model for id %s", origEntity.getId());
                return existingModel;
            }

            TModel adapter = createNewModel(realm, origEntity);

            session.getTransactionManager().enlistAfterCompletion((CassandraModelTransaction) () -> {
                log.tracef("Flush model with id %s", adapter.getId());
                adapter.commit();
                models.remove(adapter.getId());
            });
            models.put(adapter.getId(), adapter);
            return adapter;
        };
    }

    @Override
    public void close() {
        models.clear();
    }
}
