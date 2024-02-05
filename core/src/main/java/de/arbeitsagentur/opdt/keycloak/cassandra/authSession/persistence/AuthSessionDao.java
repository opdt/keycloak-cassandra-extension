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

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.Dao;
import com.datastax.oss.driver.api.mapper.annotations.Delete;
import com.datastax.oss.driver.api.mapper.annotations.Select;
import com.datastax.oss.driver.api.mapper.annotations.Update;
import de.arbeitsagentur.opdt.keycloak.cassandra.BaseDao;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.AuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSession;

@Dao
public interface AuthSessionDao extends BaseDao {
  @Update(ttl = ":ttl")
  void insertOrUpdate(RootAuthenticationSession session, int ttl);

  @Update
  void insertOrUpdate(RootAuthenticationSession session);

  @Update
  void insertOrUpdate(AuthenticationSession session);

  @Update(ttl = ":ttl")
  void insertOrUpdate(AuthenticationSession session, int ttl);

  @Delete(entityClass = RootAuthenticationSession.class)
  void deleteRootAuthSession(String id);

  @Delete
  void delete(RootAuthenticationSession session);

  @Delete
  void delete(AuthenticationSession session);

  @Delete(
      entityClass = AuthenticationSession.class,
      customWhereClause = "parent_session_id = :parentSessionId")
  void deleteAuthSessions(String parentSessionId);

  @Select(customWhereClause = "parent_session_id = :parentSessionId")
  PagingIterable<AuthenticationSession> findByParentSessionId(String parentSessionId);

  @Select(customWhereClause = "id = :id")
  RootAuthenticationSession findById(String id);
}
