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
package de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.BaseDao;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.entities.LoginFailure;

@Dao
public interface LoginFailureDao extends BaseDao {
    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(LoginFailure loginFailure);

    @Select(customWhereClause = "user_id = :userId")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<LoginFailure> findByUserId(String userId);

    @Select
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<LoginFailure> findAll();

    @Delete
    @StatementAttributes(executionProfileName = "write")
    void delete(LoginFailure loginFailure);

    @Delete(entityClass = LoginFailure.class)
    @StatementAttributes(executionProfileName = "write")
    void deleteByUserId(String userId);
}
