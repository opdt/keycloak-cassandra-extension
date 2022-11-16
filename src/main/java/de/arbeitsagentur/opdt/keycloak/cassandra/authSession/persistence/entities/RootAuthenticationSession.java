package de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;
import org.keycloak.models.map.common.ExpirableEntity;

@EqualsAndHashCode(of = "id")
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("root_authentication_sessions")
public class RootAuthenticationSession implements ExpirableEntity {
  @PartitionKey
  private String id;
  private String realmId;

  private Long timestamp;
  private Long expiration;
}
