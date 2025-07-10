package de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.Groups;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalRepository;

public class CassandraGroupRepository extends TransactionalRepository<Groups, GroupDao> implements GroupRepository {
    public CassandraGroupRepository(GroupDao dao) {
        super(dao);
    }

    @Override
    public Groups getGroupsByRealmId(String realmId) {
        Groups groups = dao.getGroupsByRealmId(realmId);
        if (groups == null) {
            groups = Groups.builder().realmId(realmId).build();
        }

        return groups;
    }

    @Override
    public void deleteRealmGroups(String realmId) {
        dao.deleteAllRealmGroups(realmId);
    }
}
