package de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.*;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.google.common.base.Strings;

public class QueryProviders {
  public static void field(Select select, String name, String value) {
    if (!Strings.isNullOrEmpty(value)) {
      select.whereColumn(name).isEqualTo(bindMarker());
    }
  }

  public static void bind(BoundStatementBuilder boundStatementBuilder, String name, String value) {
    if (!Strings.isNullOrEmpty(value)) {
      boundStatementBuilder.setString(name, value);
    }
  }
}
