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

public interface EventRepository {
  void insertEvent(EventEntity event);

  void insertAdminEvent(AdminEventEntity adminEvent);

  void deleteRealmEvents(String realmId);

  void deleteRealmEvents(String realmId, long olderThan);

  void deleteAdminRealmEvents(String realmId);

  void deleteAdminRealmEvents(String realmId, long olderThan);

  EventQuery eventQuery();

  AdminEventQuery adminEventQuery();
}
