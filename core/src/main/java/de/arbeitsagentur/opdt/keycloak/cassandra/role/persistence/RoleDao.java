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
package de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence;

import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.Roles;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalDao;

@Dao
public interface RoleDao extends TransactionalDao<Roles> {
    @Select(customWhereClause = "realm_id = :realmId")
    @StatementAttributes(executionProfileName = "read")
    Roles getRolesByRealmId(String realmId);

    @Delete(entityClass = Roles.class, ifExists = true)
    @StatementAttributes(executionProfileName = "write")
    void deleteAllRealmRoles(String realmId);
}
