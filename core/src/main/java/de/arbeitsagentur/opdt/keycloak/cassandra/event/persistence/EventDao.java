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

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.Dao;
import com.datastax.oss.driver.api.mapper.annotations.Delete;
import com.datastax.oss.driver.api.mapper.annotations.Insert;
import com.datastax.oss.driver.api.mapper.annotations.QueryProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.BaseDao;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.AdminEventEntity;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.EventEntity;
import java.util.Date;
import java.util.List;

@Dao
public interface EventDao extends BaseDao {
  @Insert
  void insertEvent(EventEntity event);

  @Insert
  void insertAdminEvent(AdminEventEntity adminEvent);

  @Delete(entityClass = EventEntity.class, customWhereClause = "realm_id = :realmId")
  void deleteRealmEvents(String realmId);

  @Delete(
      entityClass = EventEntity.class,
      customWhereClause = "realm_id = :realmId AND time < :olderThan")
  void deleteRealmEvents(String realmId, long olderThan);

  @Delete(entityClass = AdminEventEntity.class, customWhereClause = "realm_id = :realmId")
  void deleteAdminRealmEvents(String realmId);

  @Delete(
      entityClass = AdminEventEntity.class,
      customWhereClause = "realm_id = :realmId AND time < :olderThan")
  void deleteAdminRealmEvents(String realmId, long olderThan);

  @QueryProvider(providerClass = EventQueryProvider.class, entityHelpers = EventEntity.class)
  PagingIterable<EventEntity> getEvents(
      List<String> types,
      String realmId,
      String clientId,
      String userId,
      Date fromDate,
      Date toDate,
      String ipAddress,
      Integer firstResult,
      Integer maxResults,
      boolean orderByDescTime);

  @QueryProvider(
      providerClass = AdminEventQueryProvider.class,
      entityHelpers = AdminEventEntity.class)
  PagingIterable<AdminEventEntity> getAdminEvents(
      List<String> operationTypes,
      List<String> resourceTypes,
      String realmId,
      String authRealmId,
      String authClientId,
      String authUserId,
      String authIpAddress,
      String resourcePath,
      Date fromTime,
      Date toTime,
      Integer firstResult,
      Integer maxResults,
      boolean orderByDescTime);
}
