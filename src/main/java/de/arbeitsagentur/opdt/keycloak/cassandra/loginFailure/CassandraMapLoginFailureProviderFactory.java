package de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure;

import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.AbstractCassandraProviderFactory;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserLoginFailureProviderFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

// TODO: Remove as soon as DatastoreProvider covers login failures
@JBossLog
@AutoService(UserLoginFailureProviderFactory.class)
public class CassandraMapLoginFailureProviderFactory extends AbstractCassandraProviderFactory implements UserLoginFailureProviderFactory<CassandraLoginFailureProvider>, EnvironmentDependentProviderFactory {
  private static final String PROVIDER_ID = "map";

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public CassandraLoginFailureProvider create(KeycloakSession session) {
    return new CassandraLoginFailureProvider(session, createRepository(session));
  }

  @Override
  public void init(Config.Scope scope) {

  }

  @Override
  public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
  }

  @Override
  public void close() {

  }

  @Override
  public boolean isSupported() {
    return true;
  }
}
