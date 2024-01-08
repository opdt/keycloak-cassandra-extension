/*
 * Copyright 2024 Phase Two, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
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
  public void deleteRealmEvents(String realmId) {
    dao.deleteRealmEvents(realmId);
  }
  
  @Override
  public void deleteRealmEvents(String realmId, long olderThan) {
    dao.deleteRealmEvents(realmId, olderThan);
  }

  @Override
  public void deleteAdminRealmEvents(String realmId) {
    dao.deleteAdminRealmEvents(realmId);
  }

  @Override
  public void deleteAdminRealmEvents(String realmId, long olderThan) {
    dao.deleteAdminRealmEvents(realmId, olderThan);
  }

  @Override
  public EventQuery eventQuery() {
    return new CassandraEventQuery(dao);
  }

  @Override
  public AdminEventQuery adminEventQuery() {
    return new CassandraAdminEventQuery(dao);
  }
}
