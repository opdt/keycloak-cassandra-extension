/*
 * Copyright 2024 Phase Two, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;

@EqualsAndHashCode(of = {"id"})
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("events")
public class EventEntity {
  @PartitionKey(0) private String realmId;
  @PartitionKey(1) private String id;
  @ClusteringColumn private long time;
  private String type;
  private String clientId;
  private String userId;
  private String sessionId;
  private String ipAddress;
  private String error;
  // This is the legacy field which is kept here to be able to read old events without the need to
  // migrate them
  private String detailsJson;
  private String detailsJsonLongValue;

  public String getDetailsJson() {
    return detailsJsonLongValue != null ? detailsJsonLongValue : detailsJson;
  }

  public void setDetailsJson(String detailsJson) {
    this.detailsJsonLongValue = detailsJson;
  }
}
