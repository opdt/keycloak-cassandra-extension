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

import de.arbeitsagentur.opdt.keycloak.mapstorage.common.ExpirableEntity;
import java.util.HashMap;
import java.util.Map;
import lombok.*;

@EqualsAndHashCode(of = "id")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedClientSessionValue implements ExpirableEntity {
  private String id;
  private String clientId;
  private Long timestamp;
  private Long expiration;

  private String authMethod;
  private String redirectUri;
  private String action;
  private String currentRefreshToken;
  private Integer currentRefreshTokenUseCount;
  private boolean offline;

  @Builder.Default private Map<String, String> notes = new HashMap<>();

  public Map<String, String> getNotes() {
    if (notes == null) {
      notes = new HashMap<>();
    }
    return notes;
  }
}
