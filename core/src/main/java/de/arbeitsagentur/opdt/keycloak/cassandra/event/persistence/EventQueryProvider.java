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
import com.google.common.base.Strings;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.EventEntity;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public class EventQueryProvider {
  private final CqlSession session;
  private final EntityHelper<EventEntity> eventEntityHelper;
  private final Select select;

  public EventQueryProvider(
      MapperContext context, EntityHelper<EventEntity> eventEntityHelper) {
    this.session = context.getSession();
    this.eventEntityHelper = eventEntityHelper;
    this.select = eventEntityHelper.selectStart();
  }

  public PagingIterable<EventEntity> getEvents(List<String> types, String realmId, String clientId, String userId, Date fromDate, Date toDate, String ipAddress, Integer firstResult, Integer maxResults, boolean orderByDescTime) {

    // (1) complete the query

    //types
    if (types != null && types.size() > 0) {
      select.whereColumn("operation_type").in(bindMarker());
    }
    
    //realmId
    field(select, "realm_id", realmId);

    //clientId
    field(select, "client_id", clientId);
    
    //userId
    field(select, "user_id", userId);

    //ipAddress
    field(select, "ip_address", ipAddress);

    //fromDate, toDate
    if (fromDate != null) {
      select.whereColumn("time").isGreaterThanOrEqualTo(bindMarker("from_date"));
    }
    if (toDate != null) {
      select.whereColumn("time").isLessThanOrEqualTo(bindMarker("to_date"));
    }

    //order
    select.orderBy("time", orderByDescTime ? ClusteringOrder.DESC : ClusteringOrder.ASC);

    // (2) prepare
    PreparedStatement preparedStatement = session.prepare(select.build());

    // (3) bind
    BoundStatementBuilder boundStatementBuilder = preparedStatement.boundStatementBuilder();

    //types
    if (types != null && types.size() > 0) {
      boundStatementBuilder.setList("type", types, String.class);
    }

    //realmId
    bind(boundStatementBuilder, "realm_id", realmId);

    //clientId
    bind(boundStatementBuilder, "client_id", clientId);
    
    //userId
    bind(boundStatementBuilder, "user_id", userId);

    //ipAddress
    bind(boundStatementBuilder, "ip_address", ipAddress);

    //fromDate, toDate
    if (fromDate != null) {
      boundStatementBuilder.setLocalDate("from_date", fromDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }
    if (toDate != null) {
      boundStatementBuilder.setLocalDate("to_date", toDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    //TODO range
    //firstResult, maxResults

    
    // (4) execute and map the results
    return session.execute(boundStatementBuilder.build()).map(eventEntityHelper::get);

  }
}
