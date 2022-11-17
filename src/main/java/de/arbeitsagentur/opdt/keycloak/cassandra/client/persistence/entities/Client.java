package de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.map.common.ExpirableEntity;

@EqualsAndHashCode(of = "id")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("clients")
public class Client {
    @PartitionKey
    private String realmId;

    @ClusteringColumn
    private String id;
}