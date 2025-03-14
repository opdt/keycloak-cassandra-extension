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
package de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.*;
import org.keycloak.sessions.CommonClientSessionModel;

@EqualsAndHashCode(of = {"parentSessionId", "tabId"})
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
    private Map<String, CommonClientSessionModel.ExecutionStatus> executionStatus = new HashMap<>();

    private Long timestamp;

    private String userId;
    private String clientId;
    private String redirectUri;
    private String action;
    private String protocol;

    @Builder.Default
    private Set<String> requiredActions = new HashSet<>();

    @Builder.Default
    private Set<String> clientScopes = new HashSet<>();

    @Builder.Default
    private Map<String, String> userNotes = new HashMap<>();

    @Builder.Default
    private Map<String, String> authNotes = new HashMap<>();

    @Builder.Default
    private Map<String, String> clientNotes = new HashMap<>();

    public Map<String, CommonClientSessionModel.ExecutionStatus> getExecutionStatus() {
        if (executionStatus == null) {
            executionStatus = new HashMap<>();
        }
        return executionStatus;
    }

    public Set<String> getRequiredActions() {
        if (requiredActions == null) {
            requiredActions = new HashSet<>();
        }
        return requiredActions;
    }

    public Set<String> getClientScopes() {
        if (clientScopes == null) {
            clientScopes = new HashSet<>();
        }
        return clientScopes;
    }

    public Map<String, String> getUserNotes() {
        if (userNotes == null) {
            userNotes = new HashMap<>();
        }
        return userNotes;
    }

    public Map<String, String> getAuthNotes() {
        if (authNotes == null) {
            authNotes = new HashMap<>();
        }
        return authNotes;
    }

    public Map<String, String> getClientNotes() {
        if (clientNotes == null) {
            clientNotes = new HashMap<>();
        }
        return clientNotes;
    }
}
