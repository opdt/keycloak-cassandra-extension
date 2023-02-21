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
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.Client;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalDao;

@Dao
public interface ClientDao extends TransactionalDao<Client> {
    @Select(customWhereClause = "realm_id = :realmId AND id = :id")
    @StatementAttributes(consistencyLevel = "SERIAL")
    Client getClientById(String realmId, String id);

    @Query("SELECT COUNT(id) FROM clients")
    @StatementAttributes(consistencyLevel = "SERIAL")
    long count();

    @Select(customWhereClause = "realm_id = :realmId")
    @StatementAttributes(consistencyLevel = "SERIAL")
    PagingIterable<Client> findAllClientsWithRealmId(String realmId);
}
