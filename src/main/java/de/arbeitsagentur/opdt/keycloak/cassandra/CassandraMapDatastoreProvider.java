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
package de.arbeitsagentur.opdt.keycloak.cassandra;

import de.arbeitsagentur.opdt.keycloak.cassandra.role.CassandraRoleProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.CassandraUserProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RoleProvider;
import org.keycloak.models.UserProvider;
import org.keycloak.models.map.datastore.MapDatastoreProvider;

public class CassandraMapDatastoreProvider extends MapDatastoreProvider {
  private KeycloakSession session;
  private ManagedCompositeCassandraRepository cassandraRepository;

  public CassandraMapDatastoreProvider(KeycloakSession session, ManagedCompositeCassandraRepository cassandraRepository) {
    super(session);
    this.session = session;
    this.cassandraRepository = cassandraRepository;
  }

  @Override
  public UserProvider users() {
    return new CassandraUserProvider(session, cassandraRepository);
  }

  @Override
  public RoleProvider roles() {
    return new CassandraRoleProvider(cassandraRepository);
  }

}
