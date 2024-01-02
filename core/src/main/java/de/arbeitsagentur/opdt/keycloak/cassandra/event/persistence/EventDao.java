package de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence;

import com.datastax.oss.driver.api.mapper.annotations.Dao;
import com.datastax.oss.driver.api.mapper.annotations.Delete;
import com.datastax.oss.driver.api.mapper.annotations.Insert;
import com.datastax.oss.driver.api.mapper.annotations.Select;
import com.datastax.oss.driver.api.mapper.annotations.Update;
import de.arbeitsagentur.opdt.keycloak.cassandra.BaseDao;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.AdminEventEntity;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.EventEntity;

@Dao
public interface EventDao extends BaseDao {
  @Insert
  void insertEvent(EventEntity event);

  @Insert
  void insertAdminEvent(AdminEventEntity adminEvent);
  
  @Delete(entityClass = EventEntity.class, customWhereClause = "realm_id = :realmId AND time < :olderThan")
  void deleteRealmEvents(String realmId, long olderThan);
  
  @Delete(entityClass = AdminEventEntity.class, customWhereClause = "realm_id = :realmId AND time < :olderThan")
  void deleteAdminRealmEvents(String realmId, long olderThan);
}
