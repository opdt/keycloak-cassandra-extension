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
import lombok.Data;
import java.util.Date;
import java.util.List;
import java.util.LinkedList;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Data
class CassandraEventQuery implements EventQuery {

  private final EventDao dao;
  
  private List<String> types;
  private String realmId;
  private String clientId;
  private String userId;
  private Date fromDate;
  private Date toDate;
  private String ipAddress;
  private Integer firstResult;
  private Integer maxResults;
  private boolean orderByDescTime = true;

  CassandraEventQuery(EventDao dao) {
    this.dao = dao;
  }
  
  @Override
  public EventQuery type(EventType... typesArr) {
    types = new LinkedList<String>();
    for (EventType e : typesArr) {
      types.add(e.toString());
    }
    return this;
  }

  @Override
  public EventQuery realm(String realmId) {
    this.realmId = realmId;
    return this;
  }

  @Override
  public EventQuery client(String clientId) {
    this.clientId = clientId;
    return this;
  }

  @Override
  public EventQuery user(String userId) {
    this.userId = userId;
    return this;
  }

  @Override
  public EventQuery fromDate(Date fromDate) {
    this.fromDate = fromDate;
    return this;
  }

  @Override
  public EventQuery toDate(Date toDate) {
    this.toDate = toDate;
    return this;
  }
    
  @Override
  public EventQuery ipAddress(String ipAddress) {
    this.ipAddress = ipAddress;
    return this;
  }

  @Override
  public EventQuery firstResult(int firstResult) {
    this.firstResult = firstResult;
    return this;
  }

  @Override
  public EventQuery maxResults(int maxResults) {
    this.maxResults = maxResults;
    return this;
  }

  @Override
  public EventQuery orderByDescTime() {
    orderByDescTime = true;
    return this;
  }

  @Override
  public EventQuery orderByAscTime() {
    orderByDescTime = false;
    return this;
  }

  @Override
  public Stream<Event> getResultStream() {
    return StreamSupport.stream(dao.getEvents(types, realmId, clientId, userId, fromDate, toDate, ipAddress, firstResult, maxResults, orderByDescTime).spliterator(), false)
        .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
        .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
        .map(ee -> {
            return entityToEvent(ee);
          });
  }
  
}
