package de.arbeitsagentur.opdt.keycloak.cassandra.userFederation;

import java.util.List;

import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;

import com.google.auto.service.AutoService;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
@AutoService(UserStorageProviderFactory.class)
public class CassandraUserStorageProviderFactory implements UserStorageProviderFactory<CassandraUserStorageProvider> {

    @Override
    public void init(Config.Scope config) {
        log.debugv("Init CassandraUserStorageProvider {0}", getId());
    }

    @Override
    public void close() {
        log.debugv("Close ProviderFactory {0}", getId());
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }


    @Override
    public CassandraUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        log.debugv("Create Provider {0}", getId());
        return new CassandraUserStorageProvider();
    }

    @Override
    public String getId() {
        return "cassandra";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        // this configuration is configurable in the admin-console
        return ProviderConfigurationBuilder.create() // Config via Quarkus-Properties
                .build();
    }

}