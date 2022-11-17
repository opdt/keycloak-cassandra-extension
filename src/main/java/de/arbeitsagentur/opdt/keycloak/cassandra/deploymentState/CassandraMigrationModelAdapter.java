package de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState;

import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.DeploymentStateRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.entities.DeploymentState;
import lombok.RequiredArgsConstructor;
import org.keycloak.common.util.Time;
import org.keycloak.migration.MigrationModel;

import java.security.SecureRandom;

@RequiredArgsConstructor
public class CassandraMigrationModelAdapter implements MigrationModel {
  private final DeploymentStateRepository deploymentStateRepository;
  private DeploymentState deploymentState;
  private static final int RESOURCE_TAG_LENGTH = 5;
  private static final char[] RESOURCE_TAG_CHARSET = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

  @Override
  public String getStoredVersion() {
    DeploymentState state = getDeploymentState();
    return state == null ? null : state.getVersion();
  }

  @Override
  public String getResourcesTag() {
    DeploymentState state = getDeploymentState();
    return state == null ? null : getDeploymentState().getId();
  }

  @Override
  public void setStoredVersion(String version) {
    String resourceTag = createResourceTag();
    // Make sure resource-tag is unique within current installation
    while (deploymentStateRepository.findDeploymentStateById(resourceTag) != null) {
      resourceTag = createResourceTag();
    }

    deploymentState = DeploymentState.builder()
        .id(resourceTag)
        .version(version)
        .updatedTime(Time.currentTime())
        .build();

    deploymentStateRepository.insertOrUpdate(deploymentState);
  }

  private DeploymentState getDeploymentState() {
    if (deploymentState == null) {
      deploymentState = deploymentStateRepository.findLatest();
    }

    return deploymentState;
  }

  private String createResourceTag() {
    StringBuilder sb = new StringBuilder(RESOURCE_TAG_LENGTH);
    for (int i = 0; i < RESOURCE_TAG_LENGTH; i++) {
      sb.append(RESOURCE_TAG_CHARSET[new SecureRandom().nextInt(RESOURCE_TAG_CHARSET.length)]);
    }
    return sb.toString();
  }
}
