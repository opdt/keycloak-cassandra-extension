package de.arbeitsagentur.opdt.keycloak.cassandra.connection;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

// TODO: Delete Spi-File in META-INF/services as soon as all MapStorageProvider-implementations are covered in this extension
// @AutoService(Spi.class)
public class CassandraConnectionSpi implements Spi {

    public static final String NAME = "cassandraConnection";

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<? extends Provider> getProviderClass() {
        return CassandraConnectionProvider.class;
    }

    @Override
    public Class<? extends ProviderFactory> getProviderFactoryClass() {
        return CassandraConnectionProviderFactory.class;
    }
}