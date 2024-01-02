package de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.*;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
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
    field("realm_id", realmId);

    //authRealmId
    field("auth_realm_id", authRealmId);

    //authClientId
    field("auth_client_id", authClientId);
    
    //authUserId
    field("auth_user_id", authUserId);

    //authIpAddress
    field("auth_ip_address", authIpAddress);

    //resourcePath
    field("resource_path", resourcePath);

    //fromTime, toTime
    if (fromTime != null) {
      select.whereColumn("time").isGreaterThanOrEqualTo(bindMarker("from_time"));
    }
    if (toTime != null) {
      select.whereColumn("time").isLessThanOrEqualTo(bindMarker("to_time"));
    }

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

    //TODO range, order

    // (4) execute and map the results
    return session.execute(boundStatementBuilder.build()).map(adminEventEntityHelper::get);

  }
  
  void field(String name, String value) {
    if (!Strings.isNullOrEmpty(value)) {
      select.whereColumn(name).isEqualTo(bindMarker());
    }
  }

  void bind(BoundStatementBuilder boundStatementBuilder, String name, String value) {
    if (!Strings.isNullOrEmpty(value)) {
      boundStatementBuilder.setString(name, value);
    }
  }

  
}
