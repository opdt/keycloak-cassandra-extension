package de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;
import org.keycloak.sessions.CommonClientSessionModel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EqualsAndHashCode(of = { "parentSessionId", "tabId" })
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("authentication_sessions")
public class AuthenticationSession {
  @PartitionKey
  private String parentSessionId;

  @ClusteringColumn
  private String tabId;

  @Builder.Default
  private Map<String, CommonClientSessionModel.ExecutionStatus> executionStatus = new ConcurrentHashMap<>();

  private Long timestamp;

  private String userId;
  private String clientId;
  private String redirectUri;
  private String action;
  private String protocol;

  @Builder.Default
  private Set<String> requiredActions = ConcurrentHashMap.newKeySet();

  @Builder.Default
  private Set<String> clientScopes = ConcurrentHashMap.newKeySet();

  @Builder.Default
  private Map<String,String> userNotes = new ConcurrentHashMap<>();

  @Builder.Default
  private Map<String,String> authNotes = new ConcurrentHashMap<>();

  @Builder.Default
  private Map<String,String> clientNotes = new ConcurrentHashMap<>();

  public Map<String, CommonClientSessionModel.ExecutionStatus> getExecutionStatus() {
    if(executionStatus == null){
      executionStatus = new ConcurrentHashMap<>();
    }
    return executionStatus;
  }

  public Set<String> getRequiredActions() {
    if(requiredActions == null) {
      requiredActions = ConcurrentHashMap.newKeySet();
    }
    return requiredActions;
  }

  public Set<String> getClientScopes() {
    if(clientScopes == null) {
      clientScopes = ConcurrentHashMap.newKeySet();
    }
    return clientScopes;
  }

  public Map<String, String> getUserNotes() {
    if(userNotes == null){
      userNotes = new ConcurrentHashMap<>();
    }
    return userNotes;
  }

  public Map<String, String> getAuthNotes() {
    if(authNotes == null){
      authNotes = new ConcurrentHashMap<>();
    }
    return authNotes;
  }

  public Map<String, String> getClientNotes() {
    if(clientNotes == null){
      clientNotes = new ConcurrentHashMap<>();
    }
    return clientNotes;
  }
}
