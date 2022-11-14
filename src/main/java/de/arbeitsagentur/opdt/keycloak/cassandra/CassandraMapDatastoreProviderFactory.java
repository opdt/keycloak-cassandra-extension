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
import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.CassandraRealmRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.CassandraRoleRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.RoleMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.cache.ThreadLocalCache;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.RoleMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.CassandraUserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserMapperBuilder;
import io.quarkus.arc.Arc;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.map.datastore.MapDatastoreProviderFactory;
import org.keycloak.storage.DatastoreProvider;
import org.keycloak.storage.DatastoreProviderFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@JBossLog
@AutoService(DatastoreProviderFactory.class)
public class CassandraMapDatastoreProviderFactory extends MapDatastoreProviderFactory {
  private static final String PROVIDER_ID = "cassandra-map";

  private CassandraUserRepository userRepository;
  private CassandraRoleRepository roleRepository;

  private CassandraRealmRepository realmRepository;
  private CqlSession cqlSession;

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public DatastoreProvider create(KeycloakSession session) {
    log.debug("START NEW DATASTORE TXN");
    // New "Transaction"
    ThreadLocalCache threadLocalCache = Arc.container().instance(ThreadLocalCache.class).get();
    threadLocalCache.reset();

    ManagedCompositeCassandraRepository cassandraRepository = Arc.container().instance(ManagedCompositeCassandraRepository.class).get();
    cassandraRepository.setRoleRepository(roleRepository);
    cassandraRepository.setUserRepository(userRepository);
    cassandraRepository.setRealmRepository(realmRepository);

    return new CassandraMapDatastoreProvider(session, cassandraRepository);
  }

  @Override
  public void init(Config.Scope scope) {
    super.init(scope);
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

    UserMapper userMapper = new UserMapperBuilder(cqlSession).build();
    RoleMapper roleMapper = new RoleMapperBuilder(cqlSession).build();
    RealmMapper realmMapper = new RealmMapperBuilder(cqlSession).build();

    userRepository = new CassandraUserRepository(userMapper.userDao());
    roleRepository = new CassandraRoleRepository(roleMapper.roleDao());
    realmRepository = new CassandraRealmRepository(realmMapper.realmDao());
  }

  @Override
  public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
    super.postInit(keycloakSessionFactory);
  }

  @Override
  public void close() {
    super.close();
    cqlSession.close();
  }

  @Override
  public boolean isSupported() {
    return super.isSupported();
  }

  private void createKeyspaceIfNotExists(
      CqlSession cqlSession, String keyspaceName, int replicationFactor) {
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
}
