package de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.AdminEventEntity;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.EventEntity;
import org.keycloak.events.EventQuery;
import org.keycloak.events.admin.AdminEventQuery;

public interface EventRepository {
  void insertEvent(EventEntity event);

  void insertAdminEvent(AdminEventEntity adminEvent);
  
  void deleteRealmEvents(String realmId, long olderThan);
  
  void deleteAdminRealmEvents(String realmId, long olderThan);

  EventQuery eventQuery();

  AdminEventQuery adminEventQuery();
}
