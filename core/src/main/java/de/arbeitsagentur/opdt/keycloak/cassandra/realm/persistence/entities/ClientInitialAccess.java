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
package de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;
import org.keycloak.models.map.common.ExpirableEntity;

@EqualsAndHashCode(of = "id")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("client_initial_accesses")
public class ClientInitialAccess implements ExpirableEntity {
    @PartitionKey
    private String realmId;

    @ClusteringColumn
    private String id;

    private Long timestamp;
    private Long expiration;

    private Integer count;
    private Integer remainingCount;
}
