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
package de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;
import org.keycloak.models.map.common.ExpirableEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EqualsAndHashCode(of = "id")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("authenticated_client_sessions")
public class AuthenticatedClientSession implements ExpirableEntity {
  @PartitionKey
  private String clientId;

  @ClusteringColumn
  private String userSessionId;

  private String realmId;
  private String id;
  private Long timestamp;
  private Long expiration;

  private String authMethod;
  private String redirectUri;
  private String action;
  private String currentRefreshToken;
  private Integer currentRefreshTokenUseCount;
  private boolean offline;

  private Map<String ,String> notes;

  public Map<String, String> getNotes() {
    if(notes == null) {
      notes = new ConcurrentHashMap<>();
    }
    return notes;
  }
}
