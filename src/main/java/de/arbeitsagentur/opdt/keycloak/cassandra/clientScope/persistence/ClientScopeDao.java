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
package de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence;

import com.datastax.dse.driver.api.core.graph.GraphResultSet;
import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.AttributeToClientScopeMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScope;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopeToAttributeMapping;

@Dao
public interface ClientScopeDao {
    @Insert
    void insert(ClientScope clientScope);

    @Select(customWhereClause = "id = :id")
    ClientScope getClientScopeById(String id);

    @Select
    PagingIterable<ClientScope> findAllClientScopes();

    @Update
    void insertOrUpdate(ClientScopeToAttributeMapping attributeMapping);

    @Insert
    void insert(AttributeToClientScopeMapping attributeToClientMapping);

    @Select(customWhereClause = "attribute_name = :attributeName AND attribute_value = :attributeValue")
    PagingIterable<AttributeToClientScopeMapping> findAllClientScopeMappingsByAttribute(String attributeName, String attributeValue);

    @Select(customWhereClause = "client_scope_id = :clientScopeId AND attribute_name = :attributeName")
    ClientScopeToAttributeMapping findClientScopeAttribute(String clientScopeId, String attributeName);

    @Select(customWhereClause = "client_scope_id = :clientScopeId")
    PagingIterable<ClientScopeToAttributeMapping> findAllClientScopeAttributes(String clientScopeId);

    @Delete(entityClass = ClientScopeToAttributeMapping.class)
    void deleteClientScopeAttribute(String clientScopeId, String attributeName);

    @Delete
    void deleteAttributeToClientScopeMapping(AttributeToClientScopeMapping attributeToClientMapping);

    @Delete
    void delete(ClientScope clientScope);
}
