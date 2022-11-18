package de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("client_scopes_to_attributes")
public class ClientScopeToAttributeMapping {
    @PartitionKey
    private String clientScopeId;

    @ClusteringColumn
    private String attributeName;

    private List<String> attributeValues;

    public List<String> getAttributeValues() {
        if (attributeValues == null) {
            attributeValues = new ArrayList<>();
        }
        return attributeValues;
    }
}

