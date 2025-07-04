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
package de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence;

import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalDao;

@Dao
public interface RootAuthSessionDao extends TransactionalDao<RootAuthenticationSession> {
    @Update(ttl = ":ttl")
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(RootAuthenticationSession session, int ttl);

    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(RootAuthenticationSession session);

    @Delete
    @StatementAttributes(executionProfileName = "write")
    void delete(RootAuthenticationSession session);

    @Select(customWhereClause = "id = :id")
    @StatementAttributes(executionProfileName = "read")
    RootAuthenticationSession findById(String id);
}
