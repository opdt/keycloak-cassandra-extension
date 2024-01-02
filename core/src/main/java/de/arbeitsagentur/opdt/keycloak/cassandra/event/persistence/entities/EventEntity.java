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
@CqlName("events")
public class EventEntity {
  @PartitionKey
  private String id;
  private long time;
  private String type;
  private String realmId;
  private String clientId;
  private String userId;
  private String sessionId;
  private String ipAddress;
  private String error;
  // This is the legacy field which is kept here to be able to read old events without the need to migrate them
  private String detailsJson;
  private String detailsJsonLongValue;

  public String getDetailsJson() {
    return detailsJsonLongValue != null ? detailsJsonLongValue : detailsJson;
  }
  
  public void setDetailsJson(String detailsJson) {
    this.detailsJsonLongValue = detailsJson;
  }
}
