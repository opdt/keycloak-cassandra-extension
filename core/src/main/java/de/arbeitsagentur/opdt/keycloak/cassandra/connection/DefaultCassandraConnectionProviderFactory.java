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

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.schema.CreateKeyspace;
import com.datastax.oss.driver.internal.core.type.codec.extras.enums.EnumNameCodec;
import com.datastax.oss.driver.internal.core.type.codec.extras.json.JsonCodec;
import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraJsonSerialization;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopeValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.GroupValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.RoleValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.CredentialValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSessionValue;
import lombok.extern.jbosslog.JBossLog;

import org.cognitor.cassandra.migration.Database;
import org.cognitor.cassandra.migration.MigrationConfiguration;
import org.cognitor.cassandra.migration.MigrationRepository;
import org.cognitor.cassandra.migration.MigrationTask;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserSessionModel;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.sessions.CommonClientSessionModel;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@JBossLog
@AutoService(CassandraConnectionProviderFactory.class)
public class DefaultCassandraConnectionProviderFactory implements CassandraConnectionProviderFactory<CassandraConnectionProvider>, EnvironmentDependentProviderFactory {
    public static final String PROVIDER_ID = "default";
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

        List<InetSocketAddress> contactPointsList =
            Arrays.stream(contactPoints.split(","))
                .map(cp -> new InetSocketAddress(cp, port))
                .collect(Collectors.toList());

        if (scope.getBoolean("createKeyspace", true)) {
            log.info("Create keyspace (if not exists)...");
            createDbIfNotExists(contactPointsList, username, password, localDatacenter, keyspace, replicationFactor);
        } else {
            log.info("Skipping create keyspace, assuming keyspace and tables already exist...");
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
            .addTypeCodecs(new JsonCodec<>(GroupValue.class, CassandraJsonSerialization.getMapper()))
            .addTypeCodecs(new JsonCodec<>(CredentialValue.class, CassandraJsonSerialization.getMapper()))
            .addTypeCodecs(new JsonCodec<>(AuthenticatedClientSessionValue.class, CassandraJsonSerialization.getMapper()))
            .addTypeCodecs(new JsonCodec<>(ClientScopeValue.class, CassandraJsonSerialization.getMapper()))
        .build();
    }

    private void createDbIfNotExists(List<InetSocketAddress> contactPointsList, String username, String password, String localDatacenter, String keyspace, int replicationFactor) {
        try (CqlSession createKeyspaceSession =
                CqlSession.builder()
                    .addContactPoints(contactPointsList)
                    .withAuthCredentials(username, password)
                    .withLocalDatacenter(localDatacenter)
                    .build()) {
            createKeyspaceIfNotExists(createKeyspaceSession, keyspace, replicationFactor);
        }

        log.info("Create schema...");
        try (CqlSession createKeyspaceSession =
                CqlSession.builder()
                    .addContactPoints(contactPointsList)
                    .withAuthCredentials(username, password)
                    .withLocalDatacenter(localDatacenter)
                    .withKeyspace(keyspace)
                    .build()) {
            createTables(createKeyspaceSession, keyspace);
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public String getId() {
        return PROVIDER_ID;
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
        CreateKeyspace createKeyspace = SchemaBuilder.createKeyspace(keyspaceName)
            .ifNotExists()
            .withNetworkTopologyStrategy(Map.of("replication_factor", replicationFactor)); // special dc-name to activate autodiscovery

        cqlSession.execute(createKeyspace.build());
        cqlSession.close();
    }

    private void createTables(CqlSession cqlSession, String keyspace) {
        MigrationConfiguration mgConfig = new MigrationConfiguration()
            .withKeyspaceName(keyspace);
        Database database = new Database(cqlSession, mgConfig)
            .setConsistencyLevel(ConsistencyLevel.ALL);
        MigrationTask migration = new MigrationTask(database, new MigrationRepository());
        migration.migrate();
    }

}
