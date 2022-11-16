package de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject;

import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.AbstractCassandraProviderFactory;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.SingleUseObjectProviderFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

// TODO: Remove as soon as DatastoreProvider covers singleUseObjects
@JBossLog
@AutoService(SingleUseObjectProviderFactory.class)
public class CassandraMapSingleUseObjectProviderFactory extends AbstractCassandraProviderFactory implements SingleUseObjectProviderFactory<CassandraSingleUseObjectProvider>, EnvironmentDependentProviderFactory {
  private static final String PROVIDER_ID = "map";

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public CassandraSingleUseObjectProvider create(KeycloakSession session) {
    return new CassandraSingleUseObjectProvider(session, createRepository());
  }

  @Override
  public void init(Config.Scope scope) {
    super.init(scope);
  }

  @Override
  public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
  }

  @Override
  public void close() {
    super.close();
  }

  @Override
  public boolean isSupported() {
    return true;
  }
}