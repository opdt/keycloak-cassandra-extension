package de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence;

import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.Groups;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalDao;

@Dao
public interface GroupDao extends TransactionalDao<Groups> {
    @Select(customWhereClause = "realm_id = :realmId")
    @StatementAttributes(executionProfileName = "read")
    Groups getGroupsByRealmId(String realmId);

    @Delete(entityClass = Groups.class, ifExists = true)
    @StatementAttributes(executionProfileName = "write")
    void deleteAllRealmGroups(String realmId);
}
