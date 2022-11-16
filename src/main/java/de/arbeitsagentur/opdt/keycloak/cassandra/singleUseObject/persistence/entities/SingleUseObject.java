package de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EqualsAndHashCode(of = "key")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("single_use_objects")
public class SingleUseObject {
  @PartitionKey
  private String key;

  @Builder.Default
  private Map<String, String> notes = new ConcurrentHashMap<>();

  public Map<String, String> getNotes() {
    if (notes == null) {
      notes = new ConcurrentHashMap<>();
    }
    return notes;
  }
}
