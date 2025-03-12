package de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.*;
import lombok.*;
import org.keycloak.models.GroupModel;

@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Builder
@Data
public class GroupValue {
    private String id;
    private String name;
    private String parentId;
    private String realmId;

    @Builder.Default
    private GroupModel.Type type = GroupModel.Type.REALM;

    @Builder.Default
    private Map<String, List<String>> attributes = new HashMap<>();

    @Builder.Default
    private Set<String> grantedRoles = new HashSet<>();

    @JsonSetter("type")
    public void setType(GroupModel.Type type) {
        this.type = type == null ? GroupModel.Type.REALM : type;
    }

    public Map<String, List<String>> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }

        return attributes;
    }

    public List<String> getAttribute(String name) {
        return attributes.getOrDefault(name, new ArrayList<>());
    }

    public Set<String> getGrantedRoles() {
        if (grantedRoles == null) {
            grantedRoles = new HashSet<>();
        }

        return grantedRoles;
    }

    public void addGrantedRole(String role) {
        if (!grantedRoles.contains(role)) {
            grantedRoles.add(role);
        }
    }

    public boolean removeGrantedRole(String role) {
        return grantedRoles.remove(role);
    }
}
