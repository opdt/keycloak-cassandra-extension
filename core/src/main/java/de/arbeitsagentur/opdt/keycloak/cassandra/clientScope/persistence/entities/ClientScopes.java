package de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.HasAttributes;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalEntity;
import java.util.*;
import lombok.*;

@EqualsAndHashCode(of = "realmId")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("client_scopes")
public class ClientScopes implements TransactionalEntity, HasAttributes {
    @PartitionKey
    private String realmId;

    private Long version;

    @Builder.Default
    private Set<ClientScopeValue> clientScopes = new HashSet<>();

    @Override
    public String getId() {
        return realmId;
    }

    public Set<ClientScopeValue> getClientScopes() {
        if (clientScopes == null) {
            return new HashSet<>();
        }
        return clientScopes;
    }

    public ClientScopeValue getClientScopeById(String id) {
        ClientScopeValue clientScope = clientScopes.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (clientScope == null) {
            return clientScopes.stream()
                    .filter(r -> r.getId().equals(id))
                    .findFirst()
                    .orElse(null);
        }
        return clientScope;
    }

    public void addClientScope(ClientScopeValue clientScopeValue) {
        clientScopes.add(clientScopeValue);
    }

    public boolean removeClientScope(String id) {
        return clientScopes.removeIf(s -> Objects.equals(s.getId(), id));
    }

    public Map<String, List<String>> getAttributes() {
        return Collections.emptyMap();
    }
}
