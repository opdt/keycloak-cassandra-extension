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

package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.parameters;

import com.google.common.collect.ImmutableSet;
import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraDatastoreProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.connection.CassandraConnectionProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.connection.CassandraConnectionSpi;
import de.arbeitsagentur.opdt.keycloak.cassandra.connection.DefaultCassandraConnectionProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.Config;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.KeycloakModelParameters;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;
import org.keycloak.storage.DatastoreSpi;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

public class CassandraMapStorage extends KeycloakModelParameters {
    public static final Boolean START_CONTAINER =
            Boolean.valueOf(System.getProperty("keycloak.testsuite.start-cassandra-container", "true"));

    static final Set<Class<? extends Spi>> ALLOWED_SPIS = ImmutableSet.<Class<? extends Spi>>builder()
            .add(CassandraConnectionSpi.class)
            .add(DatastoreSpi.class)
            .build();

    static final Set<Class<? extends ProviderFactory>> ALLOWED_FACTORIES =
            ImmutableSet.<Class<? extends ProviderFactory>>builder()
                    .add(CassandraConnectionProviderFactory.class)
                    .add(CassandraDatastoreProviderFactory.class)
                    .build();

    private final GenericContainer cassandraContainer = createCassandraContainer();

    public CassandraMapStorage() {
        super(ALLOWED_SPIS, ALLOWED_FACTORIES);
    }

    @Override
    public void updateConfig(Config cf) {
        cf.spi("datastore").defaultProvider("legacy").config("dir", "${project.build.directory:target}");

        cf.spi(CassandraConnectionSpi.NAME)
                .provider(DefaultCassandraConnectionProviderFactory.PROVIDER_ID)
                .config("contactPoints", START_CONTAINER ? cassandraContainer.getHost() : "localhost")
                .config("port", START_CONTAINER ? String.valueOf(cassandraContainer.getMappedPort(9042)) : "9042")
                .config("localDatacenter", "datacenter1")
                .config("keyspace", "test")
                .config("username", "cassandra")
                .config("password", "cassandra")
                .config("replicationFactor", "1");
    }

    @Override
    public void beforeSuite(Config cf) {
        if (START_CONTAINER) {
            cassandraContainer.start();
        }
    }

    @Override
    public void afterSuite() {
        if (START_CONTAINER) {
            cassandraContainer.stop();
        }
    }

    private static GenericContainer createCassandraContainer() {
        return new GenericContainer("bitnami/cassandra:4.1.3-debian-11-r0")
                .withExposedPorts(9042)
                .withEnv("CASSANDRA_DATACENTER", "datacenter1")
                // TODO: withLogConsumer
                .waitingFor(new LogMessageWaitStrategy()
                        .withRegEx(".*Starting listening for CQL clients.*")
                        .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES)));
    }
}
