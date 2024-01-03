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
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.AdminEventEntity;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public class AdminEventQueryProvider {
  private final CqlSession session;
  private final EntityHelper<AdminEventEntity> adminEventEntityHelper;
  private final Select select;

  public AdminEventQueryProvider(
      MapperContext context, EntityHelper<AdminEventEntity> adminEventEntityHelper) {
    this.session = context.getSession();
    this.adminEventEntityHelper = adminEventEntityHelper;
    this.select = adminEventEntityHelper.selectStart();
  }

  public PagingIterable<AdminEventEntity> getAdminEvents(List<String> operationTypes, List<String> resourceTypes, String realmId, String authRealmId, String authClientId, String authUserId, String authIpAddress, String resourcePath, Date fromTime, Date toTime, Integer firstResult, Integer maxResults, boolean orderByDescTime) {

    // (1) complete the query

    //operationTypes
    if (operationTypes != null && operationTypes.size() > 0) {
      select.whereColumn("operation_type").in(bindMarker());
    }
    
    //resourceTypes
    if (resourceTypes != null && resourceTypes.size() > 0) {
      select.whereColumn("resource_type").in(bindMarker());
    }

    //realmId
    field(select, "realm_id", realmId);

    //authRealmId
    field(select, "auth_realm_id", authRealmId);

    //authClientId
    field(select, "auth_client_id", authClientId);
    
    //authUserId
    field(select, "auth_user_id", authUserId);

    //authIpAddress
    field(select, "auth_ip_address", authIpAddress);

    //resourcePath
    field(select, "resource_path", resourcePath);

    //fromTime, toTime
    if (fromTime != null) {
      select.whereColumn("time").isGreaterThanOrEqualTo(bindMarker("from_time"));
    }
    if (toTime != null) {
      select.whereColumn("time").isLessThanOrEqualTo(bindMarker("to_time"));
    }

    //order
    select.orderBy("time", orderByDescTime ? ClusteringOrder.DESC : ClusteringOrder.ASC);

    // (2) prepare
    PreparedStatement preparedStatement = session.prepare(select.build());

    // (3) bind
    BoundStatementBuilder boundStatementBuilder = preparedStatement.boundStatementBuilder();

    //operationTypes
    if (operationTypes != null && operationTypes.size() > 0) {
      boundStatementBuilder.setList("operation_type", operationTypes, String.class);
    }

    //resourceTypes
    if (resourceTypes != null && resourceTypes.size() > 0) {
      boundStatementBuilder.setList("resource_type", resourceTypes, String.class);
    }

    //realmId
    bind(boundStatementBuilder, "realm_id", realmId);

    //authRealmId
    bind(boundStatementBuilder, "auth_realm_id", authRealmId);

    //authClientId
    bind(boundStatementBuilder, "auth_client_id", authClientId);
    
    //authUserId
    bind(boundStatementBuilder, "auth_user_id", authUserId);

    //authIpAddress
    bind(boundStatementBuilder, "auth_ip_address", authIpAddress);

    //resourcePath
    bind(boundStatementBuilder, "resource_path", resourcePath);

    //fromTime, toTime
    if (fromTime != null) {
      boundStatementBuilder.setLocalDate("from_time", fromTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }
    if (toTime != null) {
      boundStatementBuilder.setLocalDate("to_time", toTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    //TODO range (i.e. use firstResult, maxResults)
    // Maybe? https://gist.github.com/stevesun21/df3fa5141bd01a4f83fc
    // We're doing:
    //    .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
    //    .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
    // in the stream above this, but my guess is that will lead to disastrous
    // performance problems, as it actually causes cassandra to load all of the results.
    // Anyway, need to update this with something that skips without actually causing
    // the data to be moved from cassandra to the client
    
    // (4) execute and map the results
    return session.execute(boundStatementBuilder.build()).map(adminEventEntityHelper::get);

  }
  
}
