package de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("attributes_to_client_scopes")
public class AttributeToClientScopeMapping {
    @PartitionKey
    private String attributeName;

    @PartitionKey(1)
    private String attributeValue;

    @ClusteringColumn
    private String clientScopeId;
}