[![CI](https://github.com/opdt/keycloak-cassandra-extension/workflows/CI/badge.svg)](https://github.com/opdt/keycloak-cassandra-extension/actions?query=workflow%3ACI)
[![Maven Central](https://img.shields.io/maven-central/v/de.arbeitsagentur.opdt/keycloak-cassandra-extension.svg)](https://search.maven.org/artifact/de.arbeitsagentur.opdt/keycloak-cassandra-extension)
[![Known Vulnerabilities](https://snyk.io/test/github/opdt/keycloak-cassandra-extension/badge.svg?targetFile=core/pom.xml)](https://snyk.io/test/github/opdt/keycloak-cassandra-extension?targetFile=core/pom.xml)
[![Sonarcloud](https://sonarcloud.io/api/project_badges/measure?project=opdt_keycloak-cassandra-extension&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=opdt_keycloak-cassandra-extension)

# Cassandra storage extension for Keycloak

Uses Apache Cassandra to store and retrieve entities of all storage areas shown below.
Requires Keycloak 22.0.x with enabled Map-Storage feature.

This extensions enables users to get rid of Infinispan for caching and use Cassandra instead!
The benefits are much easier operations and a proven way for multi-site setups, where Cassandra handles all the Cross-DC Synchronizations.

Set `KC_SPI_DATASTORE_CASSANDRA_MAP_CACHE_MODE=true` (or equivalent keycloak configuration mechanisms) and configure
the default map-storage (for example via `KC_STORAGE=file`) to use this extension for cache areas (authSession, userSession, singleUseObject) only.

## Currently covered storage areas

- [ ] authorization
- [x] authSession
- [x] client
- [x] clientScope
- [ ] events
- [x] groups
- [x] loginFailure
- [x] realm
- [x] role
- [x] singleUseObject
- [x] user
- [x] userSession

## Integration guide

Configure the datastore-provider `cassandra-map` via the standard Keycloak configuration mechanism.
For example via environment variable: `KC_SPI_DATASTORE_PROVIDER=cassandra-map`.

You can still use other providers in certain areas, for example `KC_STORAGE_AREA_CLIENT=file` but then you have to
disable the area in this provider via `KC_SPI_DATASTORE_CASSANDRA_MAP_CLIENT_ENABLED=false`.

## Configuration

In order to use all of the included providers, the `map_storage`-feature of Keycloak has to be enabled. Furthermore the
included DatastoreProvider `cassandra-map` has to be activated (for example commandline
argument `--spi-datastore-provider=cassandra-map`, for alternatives like env-variables see
the [Keycloak configuration guide](https://www.keycloak.org/server/configuration)).

### Cassandra client configuration

| CLI-Parameter                                         | Description                                                                             |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------|
| --spi-cassandra-connection-default-port               | Cassandra CQL-Port                                                                      |
| --spi-cassandra-connection-default-contact-points     | Comma-separated list of cassandra node-endpoints                                        |
| --spi-cassandra-connection-default-local-datacenter   | Local datacenter name                                                                   |
| --spi-cassandra-connection-default-username           | Username                                                                                |
| --spi-cassandra-connection-default-passwort           | Password                                                                                |
| --spi-cassandra-connection-default-keyspace           | Keyspace-name (will be generated by the extension if it does not exist at startup-time) |
| --spi-cassandra-connection-default-replication-factor | Replication factor used if the extension creates the keyspace with simple strategy      |

## Deviations from standard storage providers

### User Lookup
Due to Cassandras query first nature, users can only be looked up by specific fields.
`UserProvider::searchForUserStream` supports the following subset of Keycloaks standard search attributes:
- `keycloak.session.realm.users.query.search` for a case insensitive username search
- `keycloak.session.realm.users.query.include_service_account` to include service accounts
- `email` for an email search

`UserProvider::searchForUserByUserAttributeStream` by default iterates all users in the entire database to filter for the requested attribute in-memory.
For efficient searches, attributes can be defined as **indexed attributes** by prefixing their name with **indexed.**, e.g. **indexed.businessKey**

### Conditional updates / optimistic locking
All write-queries are done conditionally via Cassandra Lightweight Transactions. Therefore we store a version column in each of the tables. To be able to use this to get notified if a conflicting change occured after data was read, the entityVersion is exposed via a **readonly attribute readonly.entityVersion**.
In order to pass a version in update operations, one can use the corresponding attribute **internal.entityVersion**.

## Development

### Private image registries

If you use a private image registry, you can use the .testcontainers file in your user directory to override all
image-registries used by the tests.
See https://www.testcontainers.org/features/image_name_substitution/

Example:

```properties
docker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy
hub.image.name.prefix=private-registry/3rd-party/
```

### Debugging

Debugging can be enabled via `mvn -Dmaven.surefire.debug verify` (Port 5005).

### Using an external cassandra instance

If you want to use an external cassandra instance on localhost (Port 9042) you can
use `mvn -Dkeycloak.testsuite.start-cassandra-container=false verify`
