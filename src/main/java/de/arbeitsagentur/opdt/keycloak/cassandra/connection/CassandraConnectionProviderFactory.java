package de.arbeitsagentur.opdt.keycloak.cassandra.connection;

import org.keycloak.provider.ProviderFactory;

public interface CassandraConnectionProviderFactory<T extends CassandraConnectionProvider> extends ProviderFactory<T> {
}
