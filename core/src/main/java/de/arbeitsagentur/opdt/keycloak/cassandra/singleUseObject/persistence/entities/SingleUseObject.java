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
package de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(of = "key")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("single_use_objects")
public class SingleUseObject {
    @PartitionKey
    private String key;

    @Builder.Default
    private Map<String, String> notes = new HashMap<>();

    public Map<String, String> getNotes() {
        if (notes == null) {
            notes = new HashMap<>();
        }
        return notes;
    }
}
