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

import com.datastax.oss.driver.api.core.cql.ResultSet;
import org.keycloak.models.ModelIllegalStateException;

public abstract class TransactionalRepository<TEntity extends TransactionalEntity, TDao extends TransactionalDao<TEntity>> {
    protected final TDao dao;

    public TransactionalRepository(TDao dao) {
        this.dao = dao;
    }

    public void insertOrUpdate(TEntity entity) {
        insertOrUpdate(entity, null);
    }

    public void insertOrUpdate(TEntity entity, Integer ttl) {
        if(ttl != null && ttl <= 0) {
            return;
        }

        if (entity.getVersion() == null) {
            entity.setVersion(1L);

            if(ttl == null) {
                dao.insert(entity);
            } else {
                dao.insert(entity, ttl);
            }
        } else {
            Long currentVersion = entity.getVersion();
            entity.incrementVersion();

            ResultSet result = ttl == null ? dao.update(entity, currentVersion) : dao.update(entity, ttl, currentVersion);

            if (!result.wasApplied()) {
                throw new ModelIllegalStateException("Entity couldn't be updated because its version " + currentVersion + " doesn't match the version in the database");
            }
        }
    }
}
