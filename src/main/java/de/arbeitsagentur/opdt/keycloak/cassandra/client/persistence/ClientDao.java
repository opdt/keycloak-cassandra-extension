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
package de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.AttributeToClientMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.Client;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.ClientToAttributeMapping;

@Dao
public interface ClientDao {
    @Insert
    void insert(Client client);

    @Insert
    void insert(AttributeToClientMapping mapping);

    @Update
    void insertOrUpdate(ClientToAttributeMapping mapping);

    @Select(customWhereClause = "realm_id = :realmId AND id = :id")
    Client getClientById(String realmId, String id);

    @Query("SELECT COUNT(id) FROM users")
    long count();

    @Select(customWhereClause = "realm_id = :realmId")
    PagingIterable<Client> findAllClientsWithRealmId(String realmId);

    @Select(customWhereClause = "client_id = :clientId")
    PagingIterable<ClientToAttributeMapping> findAllAttributes(String clientId);

    @Select(customWhereClause = "client_id = :clientId AND attribute_name = :attributeName")
    ClientToAttributeMapping findAttribute(String clientId, String attributeName);

    @Select(customWhereClause = "attribute_name = :attributeName AND attribute_value = :attributeValue")
    AttributeToClientMapping findClientByAttribute(String realmId, String attributeName, String attributeValue);

    @Delete
    void delete(Client client);

    @Delete
    boolean deleteAttributeToClientMapping(AttributeToClientMapping attributeToClientMapping);

    @Delete(entityClass = ClientToAttributeMapping.class)
    boolean deleteAttribute(String clientId, String attributeName);



}
