package de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.entities.SingleUseObject;

public interface SingleUseObjectRepository {
  SingleUseObject findSingleUseObjectByKey(String key);
  void insertOrUpdate(SingleUseObject singleUseObject, int ttl);
  void insertOrUpdate(SingleUseObject singleUseObject);
  boolean deleteSingleUseObjectByKey(String key);
}
