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
import static de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.QueryProviders.*;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.mapper.MapperContext;
import com.datastax.oss.driver.api.mapper.entity.EntityHelper;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.EventEntity;
import java.util.Date;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class EventQueryProvider {
  private final CqlSession session;
  private final EntityHelper<EventEntity> eventEntityHelper;

  public EventQueryProvider(MapperContext context, EntityHelper<EventEntity> eventEntityHelper) {
    this.session = context.getSession();
    this.eventEntityHelper = eventEntityHelper;
  }

  public PagingIterable<EventEntity> getEvents(
      List<String> types,
      String realmId,
      String clientId,
      String userId,
      Date fromDate,
      Date toDate,
      String ipAddress,
      Integer firstResult,
      Integer maxResults,
      boolean orderByDescTime) {

    // (1) complete the query
    Select select = eventEntityHelper.selectStart();

    // realmId
    select = field(select, "realm_id", realmId);

    // types
    if (types != null && types.size() > 0) {
      select = select.whereColumn("type").in(bindMarker("type"));
    }

    // clientId
    select = field(select, "client_id", clientId);

    // userId
    select = field(select, "user_id", userId);

    // ipAddress
    select = field(select, "ip_address", ipAddress);

    // fromDate, toDate
    if (fromDate != null) {
      select = select.whereColumn("time").isGreaterThanOrEqualTo(bindMarker("from_date"));
    }
    if (toDate != null) {
      select = select.whereColumn("time").isLessThanOrEqualTo(bindMarker("to_date"));
    }

    // order
    select = select.orderBy("time", orderByDescTime ? ClusteringOrder.DESC : ClusteringOrder.ASC);

    // allow filtering
    select = select.allowFiltering();

    // (2) prepare
    log.infof("cql is %s", select.asCql());
    PreparedStatement preparedStatement = session.prepare(select.build());

    // (3) bind
    BoundStatementBuilder boundStatementBuilder = preparedStatement.boundStatementBuilder();

    // realmId
    boundStatementBuilder = bind(boundStatementBuilder, "realm_id", realmId);

    // types
    if (types != null && types.size() > 0) {
      boundStatementBuilder = boundStatementBuilder.setList("type", types, String.class);
    }

    // clientId
    boundStatementBuilder = bind(boundStatementBuilder, "client_id", clientId);

    // userId
    boundStatementBuilder = bind(boundStatementBuilder, "user_id", userId);

    // ipAddress
    boundStatementBuilder = bind(boundStatementBuilder, "ip_address", ipAddress);

    // fromDate, toDate
    if (fromDate != null) {
      boundStatementBuilder = boundStatementBuilder.setLong("from_date", fromDate.getTime());
    }
    if (toDate != null) {
      boundStatementBuilder = boundStatementBuilder.setLong("to_date", toDate.getTime());
    }

    // TODO range
    // firstResult, maxResults

    // (4) execute and map the results
    return session.execute(boundStatementBuilder.build()).map(eventEntityHelper::get);
  }
}
