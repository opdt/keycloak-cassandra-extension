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

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.schema.CreateKeyspace;
import com.datastax.oss.driver.internal.core.type.codec.extras.enums.EnumNameCodec;
import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.CassandraAuthSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.cache.ThreadLocalCache;
import de.arbeitsagentur.opdt.keycloak.cassandra.connection.CassandraConnectionProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.CassandraLoginFailureRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.LoginFailureMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.LoginFailureMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.CassandraRealmRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.CassandraRoleRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.RoleMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.RoleMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.CassandraSingleUseObjectRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.SingleUseObjectMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.SingleUseObjectMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.CassandraUserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.CassandraUserSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.UserSessionMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.UserSessionMapperBuilder;
import io.quarkus.arc.Arc;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserSessionModel;
import org.keycloak.models.map.datastore.MapDatastoreProviderFactory;
import org.keycloak.sessions.CommonClientSessionModel;
import org.keycloak.storage.DatastoreProvider;
import org.keycloak.storage.DatastoreProviderFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@JBossLog
public abstract class AbstractCassandraProviderFactory {

  CassandraUserRepository userRepository;
  CassandraRoleRepository roleRepository;
  CassandraRealmRepository realmRepository;
  CassandraUserSessionRepository userSessionRepository;
  CassandraAuthSessionRepository authSessionRepository;
  CassandraLoginFailureRepository loginFailureRepository;
  CassandraSingleUseObjectRepository singleUseObjectRepository;

  protected ManagedCompositeCassandraRepository createRepository(KeycloakSession session) {
    CassandraConnectionProvider connectionProvider = session.getProvider(CassandraConnectionProvider.class);
    CqlSession cqlSession = connectionProvider.getCqlSession();

    if (userRepository == null) {
      UserMapper userMapper = new UserMapperBuilder(cqlSession).build();
      userRepository = new CassandraUserRepository(userMapper.userDao());
    }

    if (roleRepository == null) {
      RoleMapper roleMapper = new RoleMapperBuilder(cqlSession).build();
      roleRepository = new CassandraRoleRepository(roleMapper.roleDao());
    }

    if (realmRepository == null) {
      RealmMapper realmMapper = new RealmMapperBuilder(cqlSession).build();
      realmRepository = new CassandraRealmRepository(realmMapper.realmDao());
    }

    if (userSessionRepository == null) {
      UserSessionMapper userSessionMapper = new UserSessionMapperBuilder(cqlSession).build();
      userSessionRepository = new CassandraUserSessionRepository(userSessionMapper.userSessionDao());
    }

    if (authSessionRepository == null) {
      AuthSessionMapper authSessionMapper = new AuthSessionMapperBuilder(cqlSession).build();
      authSessionRepository = new CassandraAuthSessionRepository(authSessionMapper.authSessionDao());
    }

    if (loginFailureRepository == null) {
      LoginFailureMapper loginFailureMapper = new LoginFailureMapperBuilder(cqlSession).build();
      loginFailureRepository = new CassandraLoginFailureRepository(loginFailureMapper.loginFailureDao());
    }

    if (singleUseObjectRepository == null) {
      SingleUseObjectMapper singleUseObjectMapper = new SingleUseObjectMapperBuilder(cqlSession).build();
      singleUseObjectRepository = new CassandraSingleUseObjectRepository(singleUseObjectMapper.singleUseObjectDao());
    }

    ThreadLocalCache threadLocalCache = Arc.container().instance(ThreadLocalCache.class).get();
    threadLocalCache.reset();

    ManagedCompositeCassandraRepository cassandraRepository = Arc.container().instance(ManagedCompositeCassandraRepository.class).get();
    cassandraRepository.setRoleRepository(roleRepository);
    cassandraRepository.setUserRepository(userRepository);
    cassandraRepository.setRealmRepository(realmRepository);
    cassandraRepository.setUserSessionRepository(userSessionRepository);
    cassandraRepository.setAuthSessionRepository(authSessionRepository);
    cassandraRepository.setLoginFailureRepository(loginFailureRepository);
    cassandraRepository.setSingleUseObjectRepository(singleUseObjectRepository);

    return cassandraRepository;
  }
}
