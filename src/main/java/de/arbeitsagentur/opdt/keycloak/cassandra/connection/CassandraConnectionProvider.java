package de.arbeitsagentur.opdt.keycloak.cassandra.connection;

import com.datastax.oss.driver.api.core.CqlSession;
import org.keycloak.provider.Provider;

public interface CassandraConnectionProvider extends Provider {
  CqlSession getCqlSession();
}
