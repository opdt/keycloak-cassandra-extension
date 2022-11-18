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

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScope;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.NameToClientScope;

@Dao
public interface ClientScopeDao {
    @Update
    void insertOrUpdate(ClientScope clientScope);

    @Select(customWhereClause = "id = :id")
    ClientScope getClientScopeById(String id);

    @Select
    PagingIterable<ClientScope> findAllClientScopes();

    @Delete
    void delete(ClientScope clientScope);

    @Select(customWhereClause = "name = :name")
    NameToClientScope findByName(String name);

    @Delete(entityClass = NameToClientScope.class)
    void deleteNameToClientScope(String name);
    @Update
    void insertOrUpdate(NameToClientScope nameToRealm);
}
