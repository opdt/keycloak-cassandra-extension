package de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;

@EqualsAndHashCode(of = "id")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("deployment_state")
public class DeploymentState {
  @PartitionKey
  private String id;

  private String version;
  private int updatedTime;
}
