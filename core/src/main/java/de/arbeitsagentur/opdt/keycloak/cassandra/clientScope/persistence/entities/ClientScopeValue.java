package de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities;

import lombok.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Builder
@Data
public class ClientScopeValue {
    private String id;
    private String name;
    private String realmId;

    private Map<String, List<String>> attributes = new HashMap<>();

    public Map<String, List<String>> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }

        return attributes;
    }
}
