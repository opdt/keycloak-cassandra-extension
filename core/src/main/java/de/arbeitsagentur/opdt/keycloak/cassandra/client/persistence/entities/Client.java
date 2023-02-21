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
package de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalEntity;
import lombok.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EqualsAndHashCode(of = "id")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("clients")
public class Client implements TransactionalEntity {
    @PartitionKey
    private String realmId;

    @ClusteringColumn
    private String id;

    private Long version;

    @Builder.Default
    private Map<String, Set<String>> attributes = new HashMap<>();

    public Map<String, Set<String>> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return attributes;
    }

    public Set<String> getAttribute(String name) {
        return attributes.getOrDefault(name, new HashSet<>());
    }
}
