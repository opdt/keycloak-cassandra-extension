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
package de.arbeitsagentur.opdt.keycloak.cassandra.event;

import static de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.Converters.*;

import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.EventRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.AdminEventEntity;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.EventEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.events.Event;
import org.keycloak.events.EventQuery;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AdminEventQuery;
import de.arbeitsagentur.opdt.keycloak.mapstorage.common.TimeAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;

@JBossLog
@RequiredArgsConstructor
public class CassandraEventStoreProvider implements EventStoreProvider {

  private final EventRepository repository;

  @Override
  public void onEvent(Event event) {
    repository.insertEvent(eventToEntity(event));
  }

  @Override
  public void onEvent(AdminEvent event, boolean includeRepresentation) {
    repository.insertAdminEvent(adminEventToEntity(event, includeRepresentation));
  }
  
  @Override
  public EventQuery createQuery() {
    return repository.eventQuery();
  }

  @Override
  public AdminEventQuery createAdminQuery() {
    return repository.adminEventQuery();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException("clear() is deprecated and not supported in this implementation");
  }

  @Override
  public void clear(RealmModel realm) {
    repository.deleteRealmEvents(realm.getId(), System.currentTimeMillis());
  }

  @Override
  public void clear(RealmModel realm, long olderThan) {
    repository.deleteRealmEvents(realm.getId(), olderThan);
  }

  @Override
  public void clearExpiredEvents() {
    throw new UnsupportedOperationException("clearExpiredEvents() is deprecated and not supported in this implementation");
  }
      
  @Override
  public void clearAdmin() {
    throw new UnsupportedOperationException("clearAdmin() is deprecated and not supported in this implementation");
  }

  @Override
  public void clearAdmin(RealmModel realm) {
    repository.deleteAdminRealmEvents(realm.getId(), System.currentTimeMillis());
  }

  @Override
  public void clearAdmin(RealmModel realm, long olderThan) {
    repository.deleteAdminRealmEvents(realm.getId(), olderThan);
  }

  @Override
  public void close() {
    // Nothing to do
  }

}
