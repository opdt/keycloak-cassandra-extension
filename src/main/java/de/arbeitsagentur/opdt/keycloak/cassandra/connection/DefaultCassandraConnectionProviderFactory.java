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
package de.arbeitsagentur.opdt.keycloak.cassandra.connection;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.schema.CreateKeyspace;
import com.datastax.oss.driver.internal.core.type.codec.extras.enums.EnumNameCodec;
import com.datastax.oss.driver.internal.core.type.codec.extras.json.JsonCodec;
import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraJsonSerialization;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopeValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.RoleValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.CredentialValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSessionValue;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserSessionModel;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.sessions.CommonClientSessionModel;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@JBossLog
@AutoService(CassandraConnectionProviderFactory.class)
public class DefaultCassandraConnectionProviderFactory implements CassandraConnectionProviderFactory<CassandraConnectionProvider>, EnvironmentDependentProviderFactory {
  private CqlSession cqlSession;

  @Override
  public CassandraConnectionProvider create(KeycloakSession session) {
    return new CassandraConnectionProvider() {
      @Override
      public CqlSession getCqlSession() {
        return cqlSession;
      }

      @Override
      public void close() {

      }
    };
  }

  @Override
  public void init(Config.Scope scope) {
    // kc.spi.cassandra-connection.default.contactPoints
    // Env: KC_SPI_CASSANDRA_CONNECTION_DEFAULT_CONTACT_POINTS

    String contactPoints = scope.get("contactPoints");
    log.infov("Init CassandraProviderFactory with contactPoints {0}", contactPoints);

    int port = Integer.parseInt(scope.get("port"));
    String localDatacenter = scope.get("localDatacenter");
    String keyspace = scope.get("keyspace");
    String username = scope.get("username");
    String password = scope.get("password");
    int replicationFactor = Integer.parseInt(scope.get("replicationFactor"));

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
        .addTypeCodecs(new JsonCodec<>(RoleValue.class, CassandraJsonSerialization.getMapper()))
        .addTypeCodecs(new JsonCodec<>(CredentialValue.class, CassandraJsonSerialization.getMapper()))
        .addTypeCodecs(new JsonCodec<>(AuthenticatedClientSessionValue.class, CassandraJsonSerialization.getMapper()))
        .addTypeCodecs(new JsonCodec<>(ClientScopeValue.class, CassandraJsonSerialization.getMapper()))
            .build();

    // User-Tables
    createUserTable(cqlSession);
    createUserSearchIndexTable(cqlSession);
    createFederatedIdentityTable(cqlSession);
    createFederatedIdentityToUserMappingTable(cqlSession);
    createRealmToUserMappingTable(cqlSession);

    // Role-Tables
    createRolesTable(cqlSession);

    // Realm-Tables
    createRealmTable(cqlSession);
    createNameToRealmTable(cqlSession);
    createClientInitialAccessesTable(cqlSession);

    // UserSession-Tables
    createUserSessionTable(cqlSession);
    createUserSessionsToAttributesMappingTable(cqlSession);
    createAttributesToUserSessionsMappingTable(cqlSession);

    // AuthSession-Tables
    createRootAuthSessionTable(cqlSession);
    createAuthSessionTable(cqlSession);

    // LoginFailure-Tables
    createLoginFailuresTable(cqlSession);

    // SingleUseObjects-Tables
    createSingleUseObjectsTable(cqlSession);

    // Client-Tables
    createClientTables(cqlSession);

    // ClientScope-Tables
    createClientScopeTables(cqlSession);
    createNameToClientScopeTable(cqlSession);

    log.info("Schema created.");
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {

  }

  @Override
  public String getId() {
    return "default";
  }

  @Override
  public boolean isSupported() {
    return true;
  }

  @Override
  public void close() {
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
            .withColumn("username", DataTypes.TEXT)
            .withColumn("email", DataTypes.TEXT)
            .withColumn("first_name", DataTypes.TEXT)
            .withColumn("last_name", DataTypes.TEXT)
            .withColumn("username_case_insensitive", DataTypes.TEXT)
            .withColumn("service_account_client_link", DataTypes.TEXT)
            .withColumn("federation_link", DataTypes.TEXT)
            .withColumn("enabled", DataTypes.BOOLEAN)
            .withColumn("email_verified", DataTypes.BOOLEAN)
            .withColumn("service_account", DataTypes.BOOLEAN)
            .withColumn("created_timestamp", DataTypes.TIMESTAMP)
            .withColumn("credentials", DataTypes.setOf(DataTypes.TEXT))
            .withColumn("required_actions", DataTypes.setOf(DataTypes.TEXT))
            .withColumn("realm_roles", DataTypes.setOf(DataTypes.TEXT))
            .withColumn("client_roles", DataTypes.mapOf(DataTypes.TEXT, DataTypes.frozenSetOf(DataTypes.TEXT)))
            .withColumn("attributes", DataTypes.mapOf(DataTypes.TEXT, DataTypes.frozenListOf(DataTypes.TEXT)))
            .build();

    session.execute(statement);
  }

  private void createUserSearchIndexTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("user_search_index")
            .ifNotExists()
            .withPartitionKey("realm_id", DataTypes.TEXT)
            .withPartitionKey("name", DataTypes.TEXT)
            .withPartitionKey("value", DataTypes.TEXT)
            .withClusteringColumn("user_id", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

  private void createRealmTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("realms")
            .ifNotExists()
            .withPartitionKey("id", DataTypes.TEXT)
            .withColumn("name", DataTypes.TEXT)
            .withColumn("attributes", DataTypes.mapOf(DataTypes.TEXT, DataTypes.frozenSetOf(DataTypes.TEXT)))
            .build();

    session.execute(statement);
  }

  private void createNameToRealmTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("name_to_realm")
            .ifNotExists()
            .withPartitionKey("name", DataTypes.TEXT)
            .withColumn("id", DataTypes.TEXT)
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
            .withColumn("client_sessions", DataTypes.mapOf(DataTypes.TEXT, DataTypes.TEXT))
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

  private void createRolesTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("roles")
            .ifNotExists()
            .withPartitionKey("realm_id", DataTypes.TEXT)
            .withColumn("realm_roles", DataTypes.frozenSetOf(DataTypes.TEXT))
            .withColumn("client_roles", DataTypes.mapOf(DataTypes.TEXT, DataTypes.frozenSetOf(DataTypes.TEXT)))
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


  private void createClientTables(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("clients")
            .ifNotExists()
            .withPartitionKey("realm_id", DataTypes.TEXT)
            .withClusteringColumn("id", DataTypes.TEXT)
            .withColumn("attributes", DataTypes.mapOf(DataTypes.TEXT, DataTypes.frozenSetOf(DataTypes.TEXT)))
            .build();

    session.execute(statement);
  }

  private void createClientScopeTables(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("client_scopes")
            .ifNotExists()
            .withPartitionKey("realm_id", DataTypes.TEXT)
            .withColumn("client_scopes", DataTypes.frozenSetOf(DataTypes.TEXT))
            .build();

    session.execute(statement);
  }

  private void createNameToClientScopeTable(CqlSession session) {
    SimpleStatement statement =
        SchemaBuilder.createTable("name_to_client_scope")
            .ifNotExists()
            .withPartitionKey("realm_id", DataTypes.TEXT)
            .withClusteringColumn("name", DataTypes.TEXT)
            .withColumn("id", DataTypes.TEXT)
            .build();

    session.execute(statement);
  }

}
