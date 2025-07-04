package de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.Groups;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CassandraGroupRepository extends TransactionalRepository implements GroupRepository {

    private final GroupDao dao;

    @Override
    public void insertOrUpdate(Groups groups) {
        super.insertOrUpdateLwt(dao, groups);
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
