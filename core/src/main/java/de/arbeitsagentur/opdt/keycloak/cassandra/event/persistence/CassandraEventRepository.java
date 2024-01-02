package de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.AdminEventEntity;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.EventEntity;
import org.keycloak.events.EventQuery;
import org.keycloak.events.admin.AdminEventQuery;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CassandraEventRepository implements EventRepository {

  private final EventDao dao;

  @Override
  public void insertEvent(EventEntity event) {
    dao.insertEvent(event);
  }
    
  @Override
  public void insertAdminEvent(AdminEventEntity adminEvent) {
    dao.insertAdminEvent(adminEvent);
  }
  
  @Override
  public void deleteRealmEvents(String realmId, long olderThan) {
    dao.deleteRealmEvents(realmId, olderThan);
  }

  @Override
  public void deleteAdminRealmEvents(String realmId, long olderThan) {
    dao.deleteAdminRealmEvents(realmId, olderThan);
  }

  @Override
  public EventQuery eventQuery() {
    return null;
  }

  @Override
  public AdminEventQuery adminEventQuery() {
    return null;
  }
}
