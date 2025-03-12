package de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence;

import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.Groups;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalDao;

@Dao
public interface GroupDao extends TransactionalDao<Groups> {
    @Select(customWhereClause = "realm_id = :realmId")
    Groups getGroupsByRealmId(String realmId);

    @Delete(entityClass = Groups.class, ifExists = true)
    void deleteAllRealmGroups(String realmId);
}
