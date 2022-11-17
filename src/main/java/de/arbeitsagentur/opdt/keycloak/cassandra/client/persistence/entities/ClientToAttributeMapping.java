package de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities;

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
@CqlName("clients_to_attributes")
public class ClientToAttributeMapping {
    @PartitionKey
    private String clientId;

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

