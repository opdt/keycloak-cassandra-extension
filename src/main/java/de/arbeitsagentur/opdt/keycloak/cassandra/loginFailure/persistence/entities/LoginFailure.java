package de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
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
@CqlName("login_failures")
public class LoginFailure {
  @PartitionKey
  private String userId;

  @ClusteringColumn
  private String id;

  private String realmId;
  private Long failedLoginNotBefore;
  private Integer numFailures;
  private Long lastFailure;
  private String lastIpFailure;
}
