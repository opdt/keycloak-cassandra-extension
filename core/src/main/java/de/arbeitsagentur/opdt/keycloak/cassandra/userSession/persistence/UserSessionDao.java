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
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AttributeToUserSessionMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSessionToAttributeMapping;

import java.util.List;

@Dao
public interface UserSessionDao {
    @Update
    void insertOrUpdate(UserSession session);

    @Update(ttl = ":ttl")
    void insertOrUpdate(UserSession session, int ttl);

    @Select(customWhereClause = "id = :id")
    UserSession findById(String id);

    @Select(customWhereClause = "id IN :ids")
    PagingIterable<UserSession> findByIds(List<String> ids);

    @Select
    PagingIterable<UserSession> findAll();

    @Delete
    void deleteUserSession(UserSession session);

    @Delete(entityClass = UserSession.class)
    void deleteUserSession(String id);

    // Attributes
    @Insert
    // Tabelle hat keine Non-PK-Columns -> Update nicht möglich, stattdessen Delete + Insert
    void insert(AttributeToUserSessionMapping mapping);

    @Update
    void insert(UserSessionToAttributeMapping mapping);

    @Select(customWhereClause = "user_session_id = :userSessionId AND attribute_name = :attributeName")
    UserSessionToAttributeMapping findAttribute(String userSessionId, String attributeName);

    @Select(customWhereClause = "user_session_id = :userSessionId")
    PagingIterable<UserSessionToAttributeMapping> findAllAttributes(String userSessionId);

    @Select(customWhereClause = "attribute_name = :attributeName AND attribute_value = :attributeValue")
    PagingIterable<AttributeToUserSessionMapping> findByAttribute(String attributeName, String attributeValue);

    @Delete
    boolean deleteAttributeToUserSessionMapping(AttributeToUserSessionMapping mapping);

    @Delete(entityClass = AttributeToUserSessionMapping.class)
    boolean deleteAttributeToUserSessionMapping(String attributeName, String attributeValue, String userSessionId);

    @Delete(entityClass = UserSessionToAttributeMapping.class)
    boolean deleteAllUserSessionToAttributeMappings(String userSessionId);

    @Delete(entityClass = UserSessionToAttributeMapping.class)
    boolean deleteAttribute(String userSessionId, String attributeName);
}
