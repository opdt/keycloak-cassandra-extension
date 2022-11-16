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

  private CassandraUserRepository userRepository;
  private CassandraRoleRepository roleRepository;
  private CassandraRealmRepository realmRepository;
  private CassandraUserSessionRepository userSessionRepository;
  private CassandraAuthSessionRepository authSessionRepository;
  private CassandraLoginFailureRepository loginFailureRepository;
  private CassandraSingleUseObjectRepository singleUseObjectRepository;

  private static CqlSession cqlSession;

  protected ManagedCompositeCassandraRepository createRepository() {
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

  protected void init(Config.Scope scope) {
    // kc.spi.datastore.cassandra-map.contactPoints
    // Env: KC_SPI_DATASTORE_CASSANDRA_MAP_CONTACT_POINTS

    String contactPoints = scope.get("contactPoints");
    log.infov("Init CassandraProviderFactory with contactPoints {0}", contactPoints);

    int port = Integer.parseInt(scope.get("port"));
    String localDatacenter = scope.get("localDatacenter");
    String keyspace = scope.get("keyspace");
    String username = scope.get("username");
    String password = scope.get("password");
    int replicationFactor = Integer.parseInt(scope.get("replicationFactor"));

    if (cqlSession == null) {
      log.info("Create schema...");
      List<InetSocketAddress> contactPointsList =
          Arrays.stream(contactPoints.split(","))
              .map(cp -> new InetSocketAddress(cp, port))
              .collect(Collectors.toList());

      try (CqlSession createKeyspaceSession =
               CqlSession.builder()
                   .addContactPoints(contactPointsList)
                   .withAuthCredentials(username, password)
                   .withLocalDatacenter(localDatacenter)
                   .build()) {
        createKeyspaceIfNotExists(createKeyspaceSession, keyspace, replicationFactor);
      }

      cqlSession = CqlSession.builder()
          .addContactPoints(contactPointsList)
          .withAuthCredentials(username, password)
          .withLocalDatacenter(localDatacenter)
          .withKeyspace(keyspace)
          .addTypeCodecs(new EnumNameCodec<>(UserSessionModel.State.class))
          .addTypeCodecs(new EnumNameCodec<>(UserSessionModel.SessionPersistenceState.class))
          .addTypeCodecs(new EnumNameCodec<>(CommonClientSessionModel.ExecutionStatus.class))
          .build();

      createUserTable(cqlSession);
      createFederatedIdentityTable(cqlSession);
      createFederatedIdentityToUserMappingTable(cqlSession);
      createUsersToAttributesMappingTable(cqlSession);
      createAttributesToUsersMappingTable(cqlSession);
      createUserRoleMappingTable(cqlSession);
      createUserClientRoleMappingTable(cqlSession);
      createUserRequiredActionTable(cqlSession);
      createCredentialsTable(cqlSession);
      createRoleTable(cqlSession);
      createClientRoleTable(cqlSession);
      createRealmRoleTable(cqlSession);
      createAttributesToRolesMappingTable(cqlSession);
      createRolesToAttributesMappingTable(cqlSession);
      createRealmToUserMappingTable(cqlSession);

      // Realm-Tables
      createRealmTable(cqlSession);
      createRealmsToAttributesMappingTable(cqlSession);
      createAttributesToRealmsMappingTable(cqlSession);
      createClientInitialAccessesTable(cqlSession);

      // UserSession-Tables
      createUserSessionTable(cqlSession);
      createAuthenticatedClientSessionTable(cqlSession);
      createUserSessionsToAttributesMappingTable(cqlSession);
      createAttributesToUserSessionsMappingTable(cqlSession);

      // AuthSession-Tables
      createRootAuthSessionTable(cqlSession);
      createAuthSessionTable(cqlSession);

      // LoginFailure-Tables
      createLoginFailuresTable(cqlSession);

      // SingleUseObjects-Tables
      createSingleUseObjectsTable(cqlSession);

      log.info("Schema created.");
    }

    UserMapper userMapper = new UserMapperBuilder(cqlSession).build();
    RoleMapper roleMapper = new RoleMapperBuilder(cqlSession).build();
    RealmMapper realmMapper = new RealmMapperBuilder(cqlSession).build();
    UserSessionMapper userSessionMapper = new UserSessionMapperBuilder(cqlSession).build();
    AuthSessionMapper authSessionMapper = new AuthSessionMapperBuilder(cqlSession).build();
    LoginFailureMapper loginFailureMapper = new LoginFailureMapperBuilder(cqlSession).build();
    SingleUseObjectMapper singleUseObjectMapper = new SingleUseObjectMapperBuilder(cqlSession).build();

    userRepository = new CassandraUserRepository(userMapper.userDao());
    roleRepository = new CassandraRoleRepository(roleMapper.roleDao());
    realmRepository = new CassandraRealmRepository(realmMapper.realmDao());
    userSessionRepository = new CassandraUserSessionRepository(userSessionMapper.userSessionDao());
    authSessionRepository = new CassandraAuthSessionRepository(authSessionMapper.authSessionDao());
    loginFailureRepository = new CassandraLoginFailureRepository(loginFailureMapper.loginFailureDao());
    singleUseObjectRepository = new CassandraSingleUseObjectRepository(singleUseObjectMapper.singleUseObjectDao());
  }

  protected void close() {
    cqlSession.close();
  }

  private void createKeyspaceIfNotExists(CqlSession cqlSession, String keyspaceName, int replicationFactor) {
    CreateKeyspace createKeyspace =
        SchemaBuilder.createKeyspace(keyspaceName)
            .ifNotExists()
            .withSimpleStrategy(replicationFactor);

    cqlSession.execute(createKeyspace.build());
  }

  private void createUserTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("users")
            .ifNotExists()
            .withPartitionKey("realm_id", DataTypes.TEXT)
            .withPartitionKey("id", DataTypes.TEXT)
            .withColumn("enabled", DataTypes.BOOLEAN)
            .withColumn("email_verified", DataTypes.BOOLEAN)
            .withColumn("service_account", DataTypes.BOOLEAN)
            .withColumn("created_timestamp", DataTypes.TIMESTAMP)
            .build();

    session.execute(statement);
  }

  private void createRealmTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("realms")
            .ifNotExists()
            .withPartitionKey("id", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createUserSessionTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("user_sessions")
            .ifNotExists()
            .withPartitionKey("id", DataTypes.TEXT)
            .withColumn("realm_id", DataTypes.TEXT)
            .withColumn("user_id", DataTypes.TEXT)
            .withColumn("login_username", DataTypes.TEXT)
            .withColumn("ip_address", DataTypes.TEXT)
            .withColumn("auth_method", DataTypes.TEXT)
            .withColumn("broker_session_id", DataTypes.TEXT)
            .withColumn("broker_user_id", DataTypes.TEXT)
            .withColumn("timestamp", DataTypes.BIGINT)
            .withColumn("expiration", DataTypes.BIGINT)
            .withColumn("offline", DataTypes.BOOLEAN)
            .withColumn("remember_me", DataTypes.BOOLEAN)
            .withColumn("last_session_refresh", DataTypes.BIGINT)
            .withColumn("state", DataTypes.TEXT)
            .withColumn("notes", DataTypes.mapOf(DataTypes.TEXT, DataTypes.TEXT))
            .withColumn("persistence_state", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createRootAuthSessionTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("root_authentication_sessions")
            .ifNotExists()
            .withPartitionKey("id", DataTypes.TEXT)
            .withColumn("realm_id", DataTypes.TEXT)
            .withColumn("timestamp", DataTypes.BIGINT)
            .withColumn("expiration", DataTypes.BIGINT)
            .build();

    session.execute(statement);
  }

  private void createAuthSessionTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("authentication_sessions")
            .ifNotExists()
            .withPartitionKey("parent_session_id", DataTypes.TEXT)
            .withClusteringColumn("tab_id", DataTypes.TEXT)
            .withColumn("execution_status", DataTypes.mapOf(DataTypes.TEXT, DataTypes.TEXT))
            .withColumn("timestamp", DataTypes.BIGINT)
            .withColumn("user_id", DataTypes.TEXT)
            .withColumn("client_id", DataTypes.TEXT)
            .withColumn("redirect_uri", DataTypes.TEXT)
            .withColumn("action", DataTypes.TEXT)
            .withColumn("protocol", DataTypes.TEXT)
            .withColumn("required_actions", DataTypes.setOf(DataTypes.TEXT))
            .withColumn("client_scopes", DataTypes.setOf(DataTypes.TEXT))
            .withColumn("user_notes", DataTypes.mapOf(DataTypes.TEXT, DataTypes.TEXT))
            .withColumn("auth_notes", DataTypes.mapOf(DataTypes.TEXT, DataTypes.TEXT))
            .withColumn("client_notes", DataTypes.mapOf(DataTypes.TEXT, DataTypes.TEXT))
            .build();

    session.execute(statement);
  }

  private void createAuthenticatedClientSessionTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("authenticated_client_sessions")
            .ifNotExists()
            .withPartitionKey("client_id", DataTypes.TEXT)
            .withClusteringColumn("user_session_id", DataTypes.TEXT)
            .withColumn("realm_id", DataTypes.TEXT)
            .withColumn("id", DataTypes.TEXT)
            .withColumn("timestamp", DataTypes.BIGINT)
            .withColumn("expiration", DataTypes.BIGINT)
            .withColumn("auth_method", DataTypes.TEXT)
            .withColumn("redirect_uri", DataTypes.TEXT)
            .withColumn("action", DataTypes.TEXT)
            .withColumn("current_refresh_token", DataTypes.TEXT)
            .withColumn("current_refresh_token_use_count", DataTypes.INT)
            .withColumn("offline", DataTypes.BOOLEAN)
            .withColumn("notes", DataTypes.mapOf(DataTypes.TEXT, DataTypes.TEXT))
            .build();

    session.execute(statement);
  }

  private void createUserSessionsToAttributesMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("user_sessions_to_attributes")
            .ifNotExists()
            .withPartitionKey("user_session_id", DataTypes.TEXT)
            .withClusteringColumn("attribute_name", DataTypes.TEXT)
            .withColumn("attribute_values", DataTypes.listOf(DataTypes.TEXT))
            .build();

    session.execute(statement);
  }

  private void createAttributesToUserSessionsMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("attributes_to_user_sessions")
            .ifNotExists()
            .withPartitionKey("attribute_name", DataTypes.TEXT)
            .withClusteringColumn("attribute_value", DataTypes.TEXT)
            .withClusteringColumn("user_session_id", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createRealmsToAttributesMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("realms_to_attributes")
            .ifNotExists()
            .withPartitionKey("realm_id", DataTypes.TEXT)
            .withClusteringColumn("attribute_name", DataTypes.TEXT)
            .withColumn("attribute_values", DataTypes.listOf(DataTypes.TEXT))
            .build();

    session.execute(statement);
  }

  private void createAttributesToRealmsMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("attributes_to_realms")
            .ifNotExists()
            .withPartitionKey("attribute_name", DataTypes.TEXT)
            .withClusteringColumn("attribute_value", DataTypes.TEXT)
            .withClusteringColumn("realm_id", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createClientInitialAccessesTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("client_initial_accesses")
            .ifNotExists()
            .withPartitionKey("realm_id", DataTypes.TEXT)
            .withClusteringColumn("id", DataTypes.TEXT)
            .withColumn("timestamp", DataTypes.BIGINT)
            .withColumn("expiration", DataTypes.BIGINT)
            .withColumn("count", DataTypes.INT)
            .withColumn("remaining_count", DataTypes.INT)
            .build();

    session.execute(statement);
  }

  private void createFederatedIdentityTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("federated_identities")
            .ifNotExists()
            .withPartitionKey("user_id", DataTypes.TEXT)
            .withClusteringColumn("identity_provider", DataTypes.TEXT)
            .withColumn("identity_token", DataTypes.TEXT)
            .withColumn("broker_user_id", DataTypes.TEXT)
            .withColumn("realm_id", DataTypes.TEXT)
            .withColumn("broker_user_name", DataTypes.TEXT)
            .withColumn("created_timestamp", DataTypes.TIMESTAMP)
            .build();

    session.execute(statement);
  }

  private void createFederatedIdentityToUserMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("federated_identity_to_user_mapping")
            .ifNotExists()
            .withPartitionKey("broker_user_id", DataTypes.TEXT)
            .withPartitionKey("identity_provider", DataTypes.TEXT)
            .withColumn("user_id", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createUsersToAttributesMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("users_to_attributes")
            .ifNotExists()
            .withPartitionKey("user_id", DataTypes.TEXT)
            .withClusteringColumn("attribute_name", DataTypes.TEXT)
            .withColumn("attribute_values", DataTypes.listOf(DataTypes.TEXT))
            .build();

    session.execute(statement);
  }

  private void createAttributesToUsersMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("attributes_to_users")
            .ifNotExists()
            .withPartitionKey("attribute_name", DataTypes.TEXT)
            .withClusteringColumn("attribute_value", DataTypes.TEXT)
            .withClusteringColumn("user_id", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createUserRoleMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("user_realm_role_mapping")
            .ifNotExists()
            .withPartitionKey("user_id", DataTypes.TEXT)
            .withClusteringColumn("role_id", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createUserClientRoleMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("user_client_role_mapping")
            .ifNotExists()
            .withPartitionKey("user_id", DataTypes.TEXT)
            .withClusteringColumn("client_id", DataTypes.TEXT)
            .withClusteringColumn("role_id", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createUserRequiredActionTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("users_to_required_actions")
            .ifNotExists()
            .withPartitionKey("user_id", DataTypes.TEXT)
            .withClusteringColumn("required_action", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createCredentialsTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("credentials")
            .ifNotExists()
            .withPartitionKey("user_id", DataTypes.TEXT)
            .withClusteringColumn("id", DataTypes.TEXT)
            .withColumn("type", DataTypes.TEXT)
            .withColumn("name", DataTypes.TEXT)
            .withColumn("realm_id", DataTypes.TEXT)
            .withColumn("secret_data", DataTypes.TEXT)
            .withColumn("credential_data", DataTypes.TEXT)
            .withColumn("user_label", DataTypes.TEXT)
            .withColumn("priority", DataTypes.INT)
            .withColumn("created", DataTypes.BIGINT)
            .build();

    session.execute(statement);
  }

  private void createRoleTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("roles")
            .ifNotExists()
            .withPartitionKey("id", DataTypes.TEXT)
            .withClusteringColumn("name", DataTypes.TEXT)
            .withColumn("description", DataTypes.TEXT)
            .withColumn("client_role", DataTypes.BOOLEAN)
            .withColumn("client_id", DataTypes.TEXT)
            .withColumn("realm_id", DataTypes.TEXT)
            .withColumn("child_roles", DataTypes.listOf(DataTypes.TEXT))
            .build();

    session.execute(statement);
  }

  private void createClientRoleTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("client_roles")
            .ifNotExists()
            .withPartitionKey("client_id", DataTypes.TEXT)
            .withClusteringColumn("name", DataTypes.TEXT)
            .withColumn("id", DataTypes.TEXT)
            .withColumn("description", DataTypes.TEXT)
            .withColumn("realm_id", DataTypes.TEXT)
            .withColumn("child_roles", DataTypes.listOf(DataTypes.TEXT))
            .build();

    session.execute(statement);
  }

  private void createRealmRoleTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("realm_roles")
            .ifNotExists()
            .withPartitionKey("realm_id", DataTypes.TEXT)
            .withClusteringColumn("name", DataTypes.TEXT)
            .withColumn("id", DataTypes.TEXT)
            .withColumn("description", DataTypes.TEXT)
            .withColumn("child_roles", DataTypes.listOf(DataTypes.TEXT))
            .build();

    session.execute(statement);
  }

  private void createAttributesToRolesMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("attributes_to_roles")
            .ifNotExists()
            .withPartitionKey("attribute_name", DataTypes.TEXT)
            .withPartitionKey("attribute_value", DataTypes.TEXT)
            .withClusteringColumn("role_id", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createRolesToAttributesMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("roles_to_attributes")
            .ifNotExists()
            .withPartitionKey("role_id", DataTypes.TEXT)
            .withClusteringColumn("attribute_name", DataTypes.TEXT)
            .withColumn("attribute_values", DataTypes.listOf(DataTypes.TEXT))
            .build();

    session.execute(statement);
  }

  private void createRealmToUserMappingTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("realms_to_users")
            .ifNotExists()
            .withPartitionKey("realm_id", DataTypes.TEXT)
            .withClusteringColumn("service_account", DataTypes.BOOLEAN)
            .withClusteringColumn("user_id", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createLoginFailuresTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("login_failures")
            .ifNotExists()
            .withPartitionKey("user_id", DataTypes.TEXT)
            .withClusteringColumn("id", DataTypes.TEXT)
            .withColumn("realm_id", DataTypes.TEXT)
            .withColumn("failed_login_not_before", DataTypes.BIGINT)
            .withColumn("num_failures", DataTypes.INT)
            .withColumn("last_failure", DataTypes.BIGINT)
            .withColumn("last_ip_failure", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createSingleUseObjectsTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("single_use_objects")
            .ifNotExists()
            .withPartitionKey("key", DataTypes.TEXT)
            .withColumn("notes", DataTypes.mapOf(DataTypes.TEXT, DataTypes.TEXT))
            .build();

    session.execute(statement);
  }
}
