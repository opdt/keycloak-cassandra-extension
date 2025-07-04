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
package de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence;

import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.BaseDao;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.entities.SingleUseObject;

@Dao
public interface SingleUseObjectDao extends BaseDao {
    @Select(customWhereClause = "key = :key")
    @StatementAttributes(executionProfileName = "read")
    SingleUseObject findByKey(String key);

    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(SingleUseObject singleUseObject);

    @Update(ttl = ":ttl")
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(SingleUseObject singleUseObject, int ttl);

    @Delete(entityClass = SingleUseObject.class)
    @StatementAttributes(executionProfileName = "write")
    boolean delete(String key);
}
