package de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.entities.DeploymentState;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;

@RequiredArgsConstructor
public class CassandraDeploymentStateRepository implements DeploymentStateRepository {
  private final DeploymentStateDao dao;

  @Override
  public void insertOrUpdate(DeploymentState deploymentState) {
    dao.insertOrUpdate(deploymentState);
  }

  @Override
  public DeploymentState findDeploymentStateById(String id) {
    return dao.findById(id);
  }

  @Override
  public DeploymentState findLatest() {
    return dao.findAll().all().stream()
        .sorted(Comparator.comparing(DeploymentState::getUpdatedTime).reversed())
        .findFirst()
        .orElse(null);
  }
}
