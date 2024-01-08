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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.*;
import static de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.Converters.*;

import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.AdminEventEntity;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.EventEntity;
import org.keycloak.events.Event;
import org.keycloak.events.EventQuery;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AdminEventQuery;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import lombok.Data;
import java.util.Date;
import java.util.List;
import java.util.LinkedList;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Data
class CassandraAdminEventQuery implements AdminEventQuery {

  private final EventDao dao;

  private List<String> operationTypes;
  private List<String> resourceTypes;
  private String realmId;
  private String authRealmId;
  private String authClientId;
  private String authUserId;
  private String authIpAddress;
  private String resourcePath;
  private Date fromTime;
  private Date toTime;
  private Integer firstResult;
  private Integer maxResults;
  private boolean orderByDescTime = true;
  
  CassandraAdminEventQuery(EventDao dao) {
    this.dao = dao;
  }

  @Override
  public AdminEventQuery operation(OperationType... typesArr) {
    operationTypes = new LinkedList<String>();
    for (OperationType e : typesArr) {
      operationTypes.add(e.toString());
    }
    return this;
  }

  @Override
  public AdminEventQuery resourceType(ResourceType... typesArr) {
    resourceTypes = new LinkedList<String>();
    for (ResourceType e : typesArr) {
      resourceTypes.add(e.toString());
    }
    return this;
  }

  @Override
  public AdminEventQuery realm(String realmId) {
    this.realmId = realmId;
    return this;
  }

  @Override
  public AdminEventQuery authRealm(String authRealmId) {
    this.authRealmId = authRealmId;
    return this;
  }

  @Override
  public AdminEventQuery authClient(String authClientId) {
    this.authClientId = authClientId;
    return this;
  }

  @Override
  public AdminEventQuery authUser(String authUserId) {
    this.authUserId = authUserId;
    return this;
  }

  @Override
  public AdminEventQuery authIpAddress(String authIpAddress) {
    this.authIpAddress = authIpAddress;
    return this;
  }

  @Override
  public AdminEventQuery resourcePath(String resourcePath) {
    this.resourcePath = resourcePath;
    return this;
  }

  @Override
  public AdminEventQuery fromTime(Date fromTime) {
    this.fromTime = fromTime;
    return this;
  }
  
  @Override
  public AdminEventQuery toTime(Date toTime) {
    this.toTime = toTime;
    return this;
  }
  
  @Override
  public AdminEventQuery firstResult(int firstResult) {
    this.firstResult = firstResult;
    return this;
  }
  
  @Override
  public AdminEventQuery maxResults(int maxResults) {
    this.maxResults = maxResults;
    return this;
  }
  
  @Override
  public AdminEventQuery orderByDescTime() {
    orderByDescTime = true;
    return this;
  }
  
  @Override
  public AdminEventQuery orderByAscTime() {
    orderByDescTime = false;
    return this;
  }
  
  @Override
  public Stream<AdminEvent> getResultStream() {
    return StreamSupport.stream(dao.getAdminEvents(operationTypes, resourceTypes, realmId, authRealmId, authClientId, authUserId, authIpAddress, resourcePath, fromTime, toTime, firstResult, maxResults,  orderByDescTime).spliterator(), false)
        .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
        .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
        .map(ee -> {
            return entityToAdminEvent(ee);
          });
  }

}
