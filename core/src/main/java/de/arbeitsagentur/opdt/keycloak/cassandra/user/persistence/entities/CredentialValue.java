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

import lombok.*;

@EqualsAndHashCode(of = "id")
@Builder(toBuilder = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CredentialValue {
    private String id;
    private String type;
    private String name;
    private String secretData;
    private String credentialData;
    private String userLabel;
    private int priority;
    private long created; // Kein Instant o.ä. da die Repräsentation Keycloak-intern ist (z.B. millis vs
    // seconds). Datum ist u.a. relevant für PW-Expiration!
}
