package de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;
import org.keycloak.sessions.CommonClientSessionModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EqualsAndHashCode(of = {"id"})
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("admin_events")
public class AdminEventEntity {
  @PartitionKey
  private String id;
  private long time;
  private String realmId;
  private String operationType;
  private String resourceType;
  private String authRealmId;
  private String authClientId;
  private String authUserId;
  private String authIpAddress;
  private String resourcePath;
  private String representation;
  private String error;
}
