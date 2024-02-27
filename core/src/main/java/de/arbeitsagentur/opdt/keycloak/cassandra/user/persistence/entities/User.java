/*
 * Copyright 2022 IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import com.datastax.oss.driver.api.mapper.annotations.Transient;
import de.arbeitsagentur.opdt.keycloak.cassandra.AttributeTypes;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalEntity;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.*;

@EqualsAndHashCode(of = "id")
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("users")
public class User implements TransactionalEntity {

  @PartitionKey(0)
  private String realmId;

  @PartitionKey(1)
  private String id;

  private Long version;

  private String username;
  private String email;
  @Transient private Boolean hasEmailChanged = true;
  private String firstName;
  private String lastName;
  private String usernameCaseInsensitive;
  private String serviceAccountClientLink;
  @Transient private Boolean hasServiceAccountClientLinkChanged = true;
  private String federationLink;
  @Transient private Boolean hasFederationLinkChanged = true;

  @Builder.Default private Boolean enabled = true;
  @Builder.Default private Boolean emailVerified = false;

  @Builder.Default private boolean serviceAccount = false;

  @Builder.Default private Instant createdTimestamp = Instant.now();

  @Builder.Default private Set<CredentialValue> credentials = new HashSet<>();

  @Builder.Default private Set<String> requiredActions = new HashSet<>();

  @Builder.Default private Set<String> groupsMembership = new HashSet<>();
  @Builder.Default private Set<String> realmRoles = new HashSet<>();

  @Builder.Default private Map<String, Set<String>> clientRoles = new HashMap<>();

  @Builder.Default private Map<String, List<String>> attributes = new HashMap<>();

  public Map<String, List<String>> getAttributes() {
    if (attributes == null) {
      attributes = new HashMap<>();
    }
    return attributes;
  }

  public Map<String, List<String>> getIndexedAttributes() {
    if (attributes == null) {
      attributes = new HashMap<>();
    }

    return attributes.entrySet().stream()
        .filter(e -> e.getKey().startsWith(AttributeTypes.INDEXED_ATTRIBUTE_PREFIX))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public List<String> getAttribute(String name) {
    return attributes.getOrDefault(name, new ArrayList<>());
  }

  public Set<String> getRealmRoles() {
    if (realmRoles == null) {
      realmRoles = new HashSet<>();
    }
    return realmRoles;
  }

  public Map<String, Set<String>> getClientRoles() {
    if (clientRoles == null) {
      clientRoles = new HashMap<>();
    }
    return clientRoles;
  }

  public Set<String> getRequiredActions() {
    if (requiredActions == null) {
      requiredActions = new HashSet<>();
    }
    return requiredActions;
  }

  public Set<CredentialValue> getCredentials() {
    if (credentials == null) {
      credentials = new HashSet<>();
    }
    return credentials;
  }

  public List<CredentialValue> getSortedCredentials() {
    return getCredentials().stream()
        .sorted(Comparator.comparing(CredentialValue::getPriority))
        .collect(Collectors.toList());
  }

  public boolean hasCredential(String id) {
    return getCredentials().stream().anyMatch(c -> c.getId().equals(id));
  }

  public Set<String> getGroupsMembership() {
    return this.groupsMembership;
  }

  public void setGroupsMembership(Set<String> groupsMembership) {
    this.groupsMembership.clear();
    if (groupsMembership != null) this.groupsMembership.addAll(groupsMembership);
  }

  public void addGroupsMembership(String groupId) {
    this.groupsMembership.add(groupId);
  }

  public void removeGroupsMembership(String groupId) {
    this.groupsMembership.remove(groupId);
  }

  public void setEmail(String email) {
    this.hasEmailChanged = !Objects.equals(this.email, email);
    this.email = email;
  }

  public void setServiceAccountClientLink(String serviceAccountClientLink) {
    this.hasServiceAccountClientLinkChanged =
        !Objects.equals(this.serviceAccountClientLink, serviceAccountClientLink);
    this.serviceAccountClientLink = serviceAccountClientLink;
  }

  public void setFederationLink(String federationLink) {
    this.hasFederationLinkChanged = !Objects.equals(this.federationLink, federationLink);
    this.federationLink = federationLink;
  }
}
