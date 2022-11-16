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

import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.LoginFailureRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.CassandraRoleRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.RoleRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.cache.L1Cached;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.SingleUseObjectRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.CassandraUserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.UserSessionRepository;
import io.quarkus.arc.Unremovable;
import lombok.Setter;
import lombok.experimental.Delegate;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.enterprise.context.ApplicationScoped;

@Setter
@L1Cached
@Timed(unit = MetricUnits.MILLISECONDS)
@Unremovable
@ApplicationScoped
public class ManagedCompositeCassandraRepository implements RoleRepository, UserRepository, RealmRepository,
    UserSessionRepository, AuthSessionRepository, LoginFailureRepository, SingleUseObjectRepository {
  @Delegate
  private CassandraUserRepository userRepository;

  @Delegate
  private CassandraRoleRepository roleRepository;

  @Delegate
  private RealmRepository realmRepository;

  @Delegate
  private UserSessionRepository userSessionRepository;

  @Delegate
  private AuthSessionRepository authSessionRepository;

  @Delegate
  private LoginFailureRepository loginFailureRepository;

  @Delegate
  private SingleUseObjectRepository singleUseObjectRepository;
}
