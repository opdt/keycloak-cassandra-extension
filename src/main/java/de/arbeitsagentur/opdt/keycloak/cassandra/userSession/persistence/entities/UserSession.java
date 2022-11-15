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

import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.map.common.ExpirableEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.keycloak.models.UserSessionModel.CORRESPONDING_SESSION_ID;

@EqualsAndHashCode(of = "id")
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("user_sessions")
public class UserSession implements ExpirableEntity {
  @PartitionKey
  private String id;

  private String realmId;
  private String userId;
  private String loginUsername;
  private String ipAddress;
  private String authMethod;
  private String brokerSessionId;
  private String brokerUserId;
  private Long timestamp;
  private Long expiration;
  private boolean offline;
  private boolean rememberMe;
  private Long lastSessionRefresh;

  private UserSessionModel.State state;

  private Map<String,String> notes;

  private UserSessionModel.SessionPersistenceState persistenceState;

  public boolean hasCorrespondingSession() {
    return getNotes().containsKey(CORRESPONDING_SESSION_ID);
  }

  public Map<String, String> getNotes() {
    if(notes == null) {
      notes = new ConcurrentHashMap<>();
    }
    return notes;
  }
}
