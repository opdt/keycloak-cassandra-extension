package de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;
import org.keycloak.models.map.common.ExpirableEntity;

@EqualsAndHashCode(of = "id")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("client_initial_accesses")
public class ClientInitialAccess implements ExpirableEntity {
  @PartitionKey
  private String realmId;

  @ClusteringColumn
  private String id;

  private Long timestamp;
  private Long expiration;

  private Integer count;
  private Integer remainingCount;
}
