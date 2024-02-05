package de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("user_consents")
public class UserConsent {
  @PartitionKey(0)
  private String realmId;

  @ClusteringColumn(1)
  private String userId;

  @ClusteringColumn(2)
  private String clientId;

  @Builder.Default private Instant createdTimestamp = Instant.now();
  @Builder.Default private Instant lastUpdatedTimestamp = Instant.now();
  @Builder.Default private Set<String> grantedClientScopesId = new HashSet<>();

  public void addGrantedClientScopesId(String scope) {
    grantedClientScopesId.add(scope);
  }

  public boolean removeGrantedClientScopesId(String scope) {
    return grantedClientScopesId.remove(scope);
  }
}
