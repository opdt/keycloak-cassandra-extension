package de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState;

import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.DeploymentStateRepository;
import lombok.RequiredArgsConstructor;
import org.keycloak.migration.MigrationModel;
import org.keycloak.models.DeploymentStateProvider;

@RequiredArgsConstructor
public class CassandraDeploymentStateProvider implements DeploymentStateProvider {
  private final DeploymentStateRepository repository;

  @Override
  public MigrationModel getMigrationModel() {
    return new CassandraMigrationModelAdapter(repository);
  }

  @Override
  public void close() {

  }
}
