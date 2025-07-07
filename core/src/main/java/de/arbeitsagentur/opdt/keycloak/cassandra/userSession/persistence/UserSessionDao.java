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
package de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalDao;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSession;
import java.util.List;

@Dao
public interface UserSessionDao extends TransactionalDao<UserSession> {
    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(UserSession session);

    @Update(ttl = ":ttl")
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(UserSession session, int ttl);

    @Select(customWhereClause = "id = :id")
    @StatementAttributes(executionProfileName = "read")
    UserSession findById(String id);

    @Select(customWhereClause = "id IN :ids")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<UserSession> findByIds(List<String> ids);

    @Select
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<UserSession> findAll();

    @Delete
    @StatementAttributes(executionProfileName = "write")
    void deleteUserSession(UserSession session);

    @Delete(entityClass = UserSession.class)
    @StatementAttributes(executionProfileName = "write")
    void deleteUserSession(String id);
}
