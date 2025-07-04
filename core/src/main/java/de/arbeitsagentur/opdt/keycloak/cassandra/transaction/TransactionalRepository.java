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
import de.arbeitsagentur.opdt.keycloak.common.ModelIllegalStateException;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public abstract class TransactionalRepository {
    public <TEntity extends TransactionalEntity, TDao extends TransactionalDao<TEntity>> void insertOrUpdateLwt(
            TDao dao, TEntity entity) {
        insertOrUpdateLwt(dao, entity, true);
    }

    public <TEntity extends TransactionalEntity, TDao extends TransactionalDao<TEntity>> void insertOrUpdateLwt(
            TDao dao, TEntity entity, boolean strict) {
        if (entity.getVersion() == null) {
            entity.setVersion(1L);
            dao.insertLwt(entity);
        } else {
            Long currentVersion = entity.getVersion();
            entity.incrementVersion();

            ResultSet result = dao.updateLwt(entity, currentVersion);

            if (!result.wasApplied()) {
                Long dbVersion = result.one().getLong("version");
                if (strict) {
                    throw new ModelIllegalStateException("Entity couldn't be updated because its version "
                            + currentVersion
                            + " doesn't match the version in the database (" + dbVersion + ")");
                } else {
                    log.warn("Entity couldn't be updated because its version "
                            + currentVersion
                            + " doesn't match the version in the database (" + dbVersion + "). Strict == false -> Updating version.");

                    entity.setVersion(dbVersion);

                    insertOrUpdateLwt(dao, entity, true);
                }
            }
        }
    }

    public <TEntity extends TransactionalEntity, TDao extends TransactionalDao<TEntity>> void insertOrUpdateLwt(
            TDao dao, TEntity entity, int ttl) {
        insertOrUpdateLwt(dao, entity, ttl, true);
    }

    public <TEntity extends TransactionalEntity, TDao extends TransactionalDao<TEntity>> void insertOrUpdateLwt(
            TDao dao, TEntity entity, int ttl, boolean strict) {
        if (entity.getVersion() == null) {
            entity.setVersion(1L);
            dao.insertLwt(entity, ttl);
        } else {
            Long currentVersion = entity.getVersion();
            entity.incrementVersion();

            ResultSet result = dao.updateLwt(entity, ttl, currentVersion);

            if (!result.wasApplied()) {
                Long dbVersion = result.one().getLong("version");
                if (strict) {
                    throw new ModelIllegalStateException("Entity couldn't be updated because its version "
                            + currentVersion
                            + " doesn't match the version in the database (" + dbVersion + ")");
                } else {
                    log.warn("Entity couldn't be updated because its version "
                            + currentVersion
                            + " doesn't match the version in the database (" + dbVersion + "). Strict == false -> Updating version.");

                    entity.setVersion(dbVersion);

                    insertOrUpdateLwt(dao, entity, true);
                }
            }
        }
    }
}
