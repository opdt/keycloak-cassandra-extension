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
    field("realm_id", realmId);

    //clientId
    field("client_id", clientId);
    
    //userId
    field("user_id", userId);

    //ipAddress
    field("ip_address", ipAddress);

    //fromDate, toDate
    if (fromDate != null) {
      select.whereColumn("time").isGreaterThanOrEqualTo(bindMarker("from_date"));
    }
    if (toDate != null) {
      select.whereColumn("time").isLessThanOrEqualTo(bindMarker("to_date"));
    }

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

    //TODO range, order
    
    // (4) execute and map the results
    return session.execute(boundStatementBuilder.build()).map(eventEntityHelper::get);

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
