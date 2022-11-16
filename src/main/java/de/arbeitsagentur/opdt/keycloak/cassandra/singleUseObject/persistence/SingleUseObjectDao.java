package de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence;

import com.datastax.oss.driver.api.mapper.annotations.Dao;
import com.datastax.oss.driver.api.mapper.annotations.Delete;
import com.datastax.oss.driver.api.mapper.annotations.Select;
import com.datastax.oss.driver.api.mapper.annotations.Update;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.entities.SingleUseObject;

@Dao
public interface SingleUseObjectDao {
  @Select(customWhereClause = "key = :key")
  SingleUseObject findByKey(String key);

  @Update
  void insertOrUpdate(SingleUseObject singleUseObject);

  @Update(ttl = ":ttl")
  void insertOrUpdate(SingleUseObject singleUseObject, int ttl);

  @Delete(entityClass = SingleUseObject.class)
  boolean delete(String key);
}
