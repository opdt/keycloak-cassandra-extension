package de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.entities.DeploymentState;

public interface DeploymentStateRepository {
  void insertOrUpdate(DeploymentState deploymentState);
  DeploymentState findDeploymentStateById(String id);
  DeploymentState findLatest();
}
