package de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.Groups;

public interface GroupRepository {
  void insertOrUpdate(Groups groups);

  Groups getGroupsByRealmId(String realmId);

  void deleteRealmGroups(String realmId);
}
