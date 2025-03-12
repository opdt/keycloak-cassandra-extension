package de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalEntity;
import java.util.*;
import java.util.stream.Collectors;
import lombok.*;

@EqualsAndHashCode(of = "realmId")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("groups")
public class Groups implements TransactionalEntity {
    @PartitionKey
    private String realmId;

    private Long version;

    @Builder.Default
    private Set<GroupValue> realmGroups = new HashSet<>();

    @Override
    public String getId() {
        return realmId;
    }

    public Set<GroupValue> getRealmGroups() {
        if (realmGroups == null) {
            return new HashSet<>();
        }
        return realmGroups;
    }

    public GroupValue getGroupById(String id) {
        return realmGroups.stream()
                .filter(group -> group.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public List<GroupValue> getRealmGroupsByParentId(String id) {
        return realmGroups.stream()
                .filter(group -> Objects.equals(group.getParentId(), id))
                .collect(Collectors.toList());
    }

    public void addRealmGroup(GroupValue group) {
        realmGroups.add(group);
    }

    public boolean removeRealmGroup(String id) {
        return realmGroups.remove(GroupValue.builder().id(id).build());
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        return Collections.emptyMap();
    }
}
