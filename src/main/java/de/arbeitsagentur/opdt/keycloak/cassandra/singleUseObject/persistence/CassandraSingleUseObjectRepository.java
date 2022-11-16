package de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.entities.SingleUseObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CassandraSingleUseObjectRepository implements SingleUseObjectRepository {
  private final SingleUseObjectDao dao;

  @Override
  public SingleUseObject findSingleUseObjectByKey(String key) {
    return dao.findByKey(key);
  }

  @Override
  public void insertOrUpdate(SingleUseObject singleUseObject, int ttl) {
    dao.insertOrUpdate(singleUseObject, ttl);
  }

  @Override
  public void insertOrUpdate(SingleUseObject singleUseObject) {
    dao.insertOrUpdate(singleUseObject);
  }

  @Override
  public boolean deleteSingleUseObjectByKey(String key) {
    return dao.delete(key);
  }
}
