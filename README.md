[![CI](https://github.com/opdt/keycloak-cassandra-extension/workflows/CI/badge.svg)](https://github.com/opdt/keycloak-cassandra-extension/actions?query=workflow%3ACI)
[![Maven Central](https://img.shields.io/maven-central/v/de.arbeitsagentur.opdt/keycloak-cassandra-extension.svg)](https://search.maven.org/artifact/de.arbeitsagentur.opdt/keycloak-cassandra-extension)
[![Known Vulnerabilities](https://snyk.io/test/github/opdt/keycloak-cassandra-extension/badge.svg?targetFile=core/pom.xml)](https://snyk.io/test/github/opdt/keycloak-cassandra-extension?targetFile=core/pom.xml)
[![Sonarcloud](https://sonarcloud.io/api/project_badges/measure?project=opdt_keycloak-cassandra-extension&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=opdt_keycloak-cassandra-extension)

# Cassandra storage extension for Keycloak

Uses Apache Cassandra to store and retrieve entities of all storage areas except authorization and events.
Requires Keycloak >= 23.0.0.

> :warning: Keycloak 23 support is still early stages and mostly untested.
Use version `1.3.2-22.0.1` for Keycloak up until 22.x.x.

## How to use

- Download the JAR from Maven Central: https://repo1.maven.org/maven2/de/arbeitsagentur/opdt/keycloak-cassandra-extension/1.1.0-22.0.1/keycloak-cassandra-extension-1.1.0-22.0.1.jar
- Put the JAR in Keycloak's providers folder
- Set "cassandra" (or "cassandra-cache" if you only want to use cassandra for caching areas) as implementation for the "datastore"-SPI, for example via ENV-variable: `KC_SPI_DATASTORE_PROVIDER=cassandra-map` (for alternatives see
the [Keycloak configuration guide](https://www.keycloak.org/server/configuration))
- Set the necessary configuration options like cassandra endpoints (see the overview below)

> :warning: **Important information:**
Since map storage has been removed from Keycloak, using different storage providers for different storage areas (like users, roles) requires you to implement your own `DatastoreProvider`.
Additionally, there are storage-relevant parts of Keycloak, which are not covered by `Datastore-SPI`, like `DeploymentStateProvider` and`PublicKeyStorageProvider`.

The following parameters might be needed in addition to the configuration options of this extension (see below):

| CLI-Parameter                                      | Description                                                                                                            |
|----------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| --features-disabled=authorization                  | Disable authorization (this is essential as otherwise Keycloak tries to use InfinispanStoreFactory at a lot of places) |
| --spi-connections-infinispan-quarkus-enabled=false | Disable Infinispan (yes this needs to be done twice)                                                                   |
| --spi-connections-infinispan-default-enabled=false | Disable Infinispan (yes this needs to be done twice)                                                                   |
| --spi-user-sessions-provider=cassandra             | Prevent infinispan-initialization for user-sessions                                                                    |
| --spi-login-failure-provider=cassandra             | Prevent infinispan-initialization login-failures                                                                       |
| --spi-authentication-sessions-provider=cassandra   | Prevent infinispan-initialization authentication-sessions                                                              |
| --spi-single-use-object-provider=cassandra         | Prevent infinispan-initialization single-use-objects                                                                   |
| --spi-global-lock-provider=none                    | Deactivate global lock                                                                                                 |
| --spi-sticky-session-encoder-provider=disabled     | Deactivate sticky sessions (backed by infinispan)                                                                      |
| --spi-deployment-state-provider=map                | Use map (forked from pre-23 Keycloak) for deployment-state                                                             |
| --spi-public-key-storage-provider=map              | Use map (forked from pre-23 Keycloak) for public-key-storage                                                           |
| --spi-connections-jpa-legacy-enabled=false         | Deactivate automatic JPA schema migration                                                                              |

## Configuration options

| CLI-Parameter                                         | Description                                                                             |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------|
| --spi-cassandra-connection-default-port               | Cassandra CQL-Port                                                                      |
| --spi-cassandra-connection-default-contact-points     | Comma-separated list of cassandra node-endpoints                                        |
| --spi-cassandra-connection-default-local-datacenter   | Local datacenter name                                                                   |
| --spi-cassandra-connection-default-username           | Username                                                                                |
| --spi-cassandra-connection-default-password           | Password                                                                                |
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

### Uniqueness across username and password

This extension supports additional checks to prevent setting username to a value that is already as email of another user and setting email to a value used as username.

To enable these checks for a realm, set its attribute `enableCheckForDuplicatesAcrossUsernameAndEmail` to `true` (default when not set: `false`)

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
