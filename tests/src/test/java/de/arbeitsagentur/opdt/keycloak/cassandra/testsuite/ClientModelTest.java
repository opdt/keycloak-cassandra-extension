/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import org.hamcrest.core.Is;
import org.hamcrest.core.IsNull;
import org.junit.Test;
import org.keycloak.models.*;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.models.utils.RepresentationToModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.AddressMapper;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 *
 * @author rmartinc
 */
@RequireProvider(RealmProvider.class)
@RequireProvider(ClientProvider.class)
@RequireProvider(RoleProvider.class)
public class ClientModelTest extends KeycloakModelTest {

    private String realmId;

    private static final String searchClientId = "My ClIeNt WITH sP%Ces and sp*ci_l Ch***cters \" ?!";

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().createRealm("realm");
        realm.setDefaultRole(s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
        this.realmId = realm.getId();
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        s.realms().removeRealm(realmId);
    }

    @Test
    public void testClientsBasics() {
        // Create client
        ClientModel originalModel = withRealm(realmId, (session, realm) -> session.clients().addClient(realm, "myClientId"));
        ClientModel searchClient = withRealm(realmId, (session, realm) -> {
            ClientModel client = session.clients().addClient(realm, searchClientId);
            client.setAlwaysDisplayInConsole(true);
            client.addRedirectUri("http://www.redirecturi.com");
            return client;
        });
        assertThat(originalModel.getId(), notNullValue());

        // Find by id
        {
            ClientModel model = withRealm(realmId, (session, realm) -> session.clients().getClientById(realm, originalModel.getId()));
            assertThat(model, notNullValue());
            assertThat(model.getId(), is(equalTo(model.getId())));
            assertThat(model.getClientId(), is(equalTo("myClientId")));
        }

        // Find by clientId
        {
            ClientModel model = withRealm(realmId, (session, realm) -> session.clients().getClientByClientId(realm, "myClientId"));
            assertThat(model, notNullValue());
            assertThat(model.getId(), is(equalTo(originalModel.getId())));
            assertThat(model.getClientId(), is(equalTo("myClientId")));
        }

        // Search by clientId
        {
            withRealm(realmId, (session, realm) -> {
                ClientModel client = session.clients().searchClientsByClientIdStream(realm, "client with", 0, 10).findFirst().orElse(null);
                assertThat(client, notNullValue());
                assertThat(client.getId(), is(equalTo(searchClient.getId())));
                assertThat(client.getClientId(), is(equalTo(searchClientId)));
                return null;
            });


            withRealm(realmId, (session, realm) -> {
                ClientModel client = session.clients().searchClientsByClientIdStream(realm, "sp*ci_l Ch***cters", 0, 10).findFirst().orElse(null);
                assertThat(client, notNullValue());
                assertThat(client.getId(), is(equalTo(searchClient.getId())));
                assertThat(client.getClientId(), is(equalTo(searchClientId)));
                return null;
            });

            withRealm(realmId, (session, realm) -> {
                ClientModel client = session.clients().searchClientsByClientIdStream(realm, " AND ", 0, 10).findFirst().orElse(null);
                assertThat(client, notNullValue());
                assertThat(client.getId(), is(equalTo(searchClient.getId())));
                assertThat(client.getClientId(), is(equalTo(searchClientId)));
                return null;
            });

            withRealm(realmId, (session, realm) -> {
                // when searching by "%" all entries are expected
                assertThat(session.clients().searchClientsByClientIdStream(realm, "%", 0, 10).count(), is(equalTo(2L)));
                return null;
            });
        }

        // using Boolean operand
        {
            Map<ClientModel, Set<String>> allRedirectUrisOfEnabledClients = withRealm(realmId, (session, realm) -> session.clients().getAllRedirectUrisOfEnabledClients(realm));
            assertThat(allRedirectUrisOfEnabledClients.values(), hasSize(1));
            assertThat(allRedirectUrisOfEnabledClients.keySet().iterator().next().getId(), is(equalTo(searchClient.getId())));
        }

        // Test storing flow binding override
        {
            // Add some override
            withRealm(realmId, (session, realm) -> {
                ClientModel clientById = session.clients().getClientById(realm, originalModel.getId());
                clientById.setAuthenticationFlowBindingOverride("browser", "customFlowId");
                return clientById;
            });

            String browser = withRealm(realmId, (session, realm) -> session.clients().getClientById(realm, originalModel.getId()).getAuthenticationFlowBindingOverride("browser"));
            assertThat(browser, is(equalTo("customFlowId")));
        }
    }

    @Test
    public void testScopeMappingRoleRemoval() {
        // create two clients, one realm role and one client role and assign both to one of the clients
        inComittedTransaction(1, (session , i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            ClientModel client1 = session.clients().addClient(realm, "client1");
            ClientModel client2 = session.clients().addClient(realm, "client2");
            RoleModel realmRole = session.roles().addRealmRole(realm, "realm-role");
            RoleModel client2Role = session.roles().addClientRole(client2, "client2-role");
            client1.addScopeMapping(realmRole);
            client1.addScopeMapping(client2Role);
            return null;
        });

        // check everything is OK
        inComittedTransaction(1, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final ClientModel client1 = session.clients().getClientByClientId(realm, "client1");
            assertThat(client1.getScopeMappingsStream().count(), is(2L));
            assertThat(client1.getScopeMappingsStream().filter(r -> r.getName().equals("realm-role")).count(), is(1L));
            assertThat(client1.getScopeMappingsStream().filter(r -> r.getName().equals("client2-role")).count(), is(1L));
            return null;
        });

        // remove the realm role
        inComittedTransaction(1, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final RoleModel role = session.roles().getRealmRole(realm, "realm-role");
            session.roles().removeRole(role);
            return null;
        });

        // check it is removed
        inComittedTransaction(1, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final ClientModel client1 = session.clients().getClientByClientId(realm, "client1");
            assertThat(client1.getScopeMappingsStream().count(), is(1L));
            assertThat(client1.getScopeMappingsStream().filter(r -> r.getName().equals("client2-role")).count(), is(1L));
            return null;
        });

        // remove client role
        inComittedTransaction(1, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final ClientModel client2 = session.clients().getClientByClientId(realm, "client2");
            final RoleModel role = session.roles().getClientRole(client2, "client2-role");
            session.roles().removeRole(role);
            return null;
        });

        // check both clients are removed
        inComittedTransaction(1, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final ClientModel client1 = session.clients().getClientByClientId(realm, "client1");
            assertThat(client1.getScopeMappingsStream().count(), is(0L));
            return null;
        });

        // remove clients
        inComittedTransaction(1, (session , i) -> {
            final RealmModel realm = session.realms().getRealm(realmId);
            final ClientModel client1 = session.clients().getClientByClientId(realm, "client1");
            final ClientModel client2 = session.clients().getClientByClientId(realm, "client2");
            session.clients().removeClient(realm, client1.getId());
            session.clients().removeClient(realm, client2.getId());
            return null;
        });
    }

    @Test
    public void testClientScopes() {
        List<String> clientScopes = new LinkedList<>();
        withRealm(realmId, (session, realm) -> {
            ClientModel client = session.clients().addClient(realm, "myClientId");

            ClientScopeModel clientScope1 = session.clientScopes().addClientScope(realm, "myClientScope1");
            clientScopes.add(clientScope1.getId());
            ClientScopeModel clientScope2 = session.clientScopes().addClientScope(realm, "myClientScope2");
            clientScopes.add(clientScope2.getId());


            client.addClientScope(clientScope1, true);
            client.addClientScope(clientScope2, false);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            List<String> actualClientScopes = session.clientScopes().getClientScopesStream(realm).map(ClientScopeModel::getId).collect(Collectors.toList());
            assertThat(actualClientScopes, containsInAnyOrder(clientScopes.toArray()));

            ClientScopeModel clientScopeById = session.clientScopes().getClientScopeById(realm, clientScopes.get(0));
            assertThat(clientScopeById.getId(), is(clientScopes.get(0)));

            session.clientScopes().removeClientScopes(realm);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            List<ClientScopeModel> actualClientScopes = session.clientScopes().getClientScopesStream(realm).collect(Collectors.toList());
            assertThat(actualClientScopes, empty());

            return null;
        });
    }

    // From Arquillian Tests
    private static void assertEquals(ClientModel expected, ClientModel actual) {
        assertThat(expected.getClientId(), Is.is(actual.getClientId()));
        assertThat(expected.getName(), Is.is(actual.getName()));
        assertThat(expected.getDescription(), Is.is(actual.getDescription()));
        assertThat(expected.getBaseUrl(), Is.is(actual.getBaseUrl()));
        assertThat(expected.getManagementUrl(), Is.is(actual.getManagementUrl()));
        assertThat(expected.getRedirectUris().containsAll(actual.getRedirectUris()), Is.is(true));
        assertThat(expected.getWebOrigins().containsAll(actual.getWebOrigins()), Is.is(true));
        assertThat(expected.getRegisteredNodes(), Is.is(actual.getRegisteredNodes()));
    }


    private ClientModel setUpClient(RealmModel realm) {
        ClientModel client = realm.addClient("application");
        client.setName("Application");
        client.setDescription("Description");
        client.setBaseUrl("http://base");
        client.setManagementUrl("http://management");
        client.setClientId("app-name");
        client.setProtocol("openid-connect");
        client.addRole("role-1");
        client.addRole("role-2");
        client.addRole("role-3");
        client.addRedirectUri("redirect-1");
        client.addRedirectUri("redirect-2");
        client.addWebOrigin("origin-1");
        client.addWebOrigin("origin-2");
        client.registerNode("node1", 10);
        client.registerNode("10.20.30.40", 50);
        client.addProtocolMapper(AddressMapper.createAddressMapper());
        client.updateClient();
        return client;
    }

    @Test
    public void testClientRoleRemovalAndClientScope() {
        // Client "from" has a role.  Assign this role to a scope to client "scoped".  Delete the role and make sure
        // cache gets cleared

        String roleId = withRealm(realmId, (session, realm) -> {
            assertThat("Realm Model 'original' is NULL !!", realm, IsNull.notNullValue());
            ClientModel from = realm.addClient("from");

            RoleModel role = from.addRole("clientRole");

            ClientModel scoped = realm.addClient("scoped");
            scoped.setFullScopeAllowed(false);
            scoped.addScopeMapping(role);
            return role.getId();
        });

        withRealm(realmId, (session, realm) -> {
            assertThat("Realm Model 'original' is NULL !!", realm, IsNull.notNullValue());
            ClientModel from = realm.getClientByClientId("from");

            RoleModel role = session.roles().getRoleById(realm, roleId);
            from.removeRole(role);
            session.clients().removeClient(realm, from.getId());
            return null;
        });

        withRealm(realmId, (session, realm) -> {
            assertThat("Realm Model 'original' is NULL !!", realm, IsNull.notNullValue());
            ClientModel scoped = realm.getClientByClientId("scoped");

            // used to throw an NPE
            assertThat("Scope Mappings must be 0", scoped.getScopeMappingsStream().count(), Is.is(0L));
            session.clients().removeClient(realm, scoped.getId());
            return null;
        });

    }

    @Test
    public void testClientRoleRemovalAndClientScopeSameTx() {
        // Client "from" has a role.  Assign this role to a scope to client "scoped".  Delete the role and make sure
        // cache gets cleared

        String roleId = withRealm(realmId, (session, realm) -> {
            ClientModel from = realm.addClient("from");
            RoleModel role = from.addRole("clientRole");
            ClientModel scoped = realm.addClient("scoped");

            scoped.setFullScopeAllowed(false);
            scoped.addScopeMapping(role);

            return role.getId();
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel from = realm.getClientByClientId("from");
            RoleModel role = session.roles().getRoleById(realm, roleId);
            from.removeRole(role);

            ClientModel scoped = realm.getClientByClientId("scoped");

            // used to throw an NPE
            assertThat("Scope Mappings is not 0", scoped.getScopeMappingsStream().count(), Is.is(0L));
            session.clients().removeClient(realm, scoped.getId());
            session.clients().removeClient(realm, from.getId());
            return null;
        });
    }

    @Test
    public void testRealmRoleRemovalAndClientScope() {
        // Client "from" has a role.  Assign this role to a scope to client "scoped".  Delete the role and make sure
        // cache gets cleared

        String roleId = withRealm(realmId, (session, realm) -> {
            RoleModel role = realm.addRole("clientRole");
            ClientModel scoped = realm.addClient("scoped");
            scoped.setFullScopeAllowed(false);
            scoped.addScopeMapping(role);

            return role.getId();
        });

        withRealm(realmId, (session, realm) -> {
            RoleModel role = session.roles().getRoleById(realm, roleId);
            realm.removeRole(role);
            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel scoped = realm.getClientByClientId("scoped");
            // used to throw an NPE
            assertThat("Scope Mappings is not 0", scoped.getScopeMappingsStream().count(), Is.is(0L));
            session.clients().removeClient(realm, scoped.getId());
            return null;
        });
    }

    @Test
    public void testCircularClientScopes() {

        withRealm(realmId, (session, realm) -> {
            ClientModel scoped1 = realm.addClient("scoped1");
            RoleModel role1 = scoped1.addRole("role1");
            ClientModel scoped2 = realm.addClient("scoped2");
            RoleModel role2 = scoped2.addRole("role2");
            scoped1.addScopeMapping(role2);
            scoped2.addScopeMapping(role1);
            return null;
        });

        withRealm(realmId, (session, realm) -> {

            // this hit the circular cache and failed with a stack overflow
            ClientModel scoped1 = realm.getClientByClientId("scoped1");
            session.clients().removeClient(realm, scoped1.getId());
            return null;
        });
    }

    @Test
    public void persist() {
        withRealm(realmId, (session, realm) -> {
            ClientModel client = setUpClient(realm);
            ClientModel actual = realm.getClientByClientId("app-name");

            assertEquals(client, actual);

            client.unregisterNode("node1");
            client.unregisterNode("10.20.30.40");

            session.clients().removeClient(realm, client.getId());
            return null;
        });
    }

    @Test
    public void json() {
        withRealm(realmId, (session, realm) -> {

            ClientModel client = setUpClient(realm);
            ClientRepresentation representation = ModelToRepresentation.toRepresentation(client, session);
            representation.setId(null);
            for (ProtocolMapperRepresentation protocolMapper : representation.getProtocolMappers()) {
                protocolMapper.setId(null);
            }

            realm = session.realms().createRealm("copy");
            ClientModel copyClient = RepresentationToModel.createClient(session, realm, representation);

            assertEquals(client, copyClient);

            client.unregisterNode("node1");
            client.unregisterNode("10.20.30.40");

            session.clients().removeClient(realm, client.getId());
            session.clients().removeClient(realm, copyClient.getId());
            session.realms().removeRealm(realm.getId());
            return null;
        });
    }

    @Test
    public void testClientScopesBinding() {
        AtomicReference<ClientScopeModel> scope1Atomic = new AtomicReference<>();
        AtomicReference<ClientScopeModel> scope2Atomic = new AtomicReference<>();
        AtomicReference<ClientScopeModel> scope3Atomic = new AtomicReference<>();

        withRealm(realmId, (session, realm) -> {
            ClientModel client = realm.addClient("templatized");
            client.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);

            ClientScopeModel scope1 = realm.addClientScope("scope1");
            scope1.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
            scope1Atomic.set(scope1);

            ClientScopeModel scope2 = realm.addClientScope("scope2");
            scope2.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
            scope2Atomic.set(scope2);

            ClientScopeModel scope3 = realm.addClientScope("scope3");
            scope3.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
            scope3Atomic.set(scope3);
            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel client = realm.getClientByClientId("templatized");

            ClientScopeModel scope1 = scope1Atomic.get();
            ClientScopeModel scope2 = scope2Atomic.get();
            ClientScopeModel scope3 = scope3Atomic.get();

            scope1 = realm.getClientScopeById(scope1.getId());
            scope2 = realm.getClientScopeById(scope2.getId());
            scope3 = realm.getClientScopeById(scope3.getId());

            client.addClientScope(scope1, true);
            client.addClientScope(scope2, false);
            client.addClientScope(scope3, false);
            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel client = realm.getClientByClientId("templatized");

            ClientScopeModel scope1 = scope1Atomic.get();
            ClientScopeModel scope2 = scope2Atomic.get();

            Map<String, ClientScopeModel> clientScopes1 = client.getClientScopes(true);
            assertThat("Client Scope contains 'scope1':", clientScopes1.containsKey("scope1"), Is.is(true));
            assertThat("Client Scope contains 'scope2':", clientScopes1.containsKey("scope2"), Is.is(false));
            assertThat("Client Scope contains 'scope3':", clientScopes1.containsKey("scope3"), Is.is(false));

            Map<String, ClientScopeModel> clientScopes2 = client.getClientScopes(false);
            assertThat("Client Scope contains 'scope1':", clientScopes2.containsKey("scope1"), Is.is(false));
            assertThat("Client Scope contains 'scope2':", clientScopes2.containsKey("scope2"), Is.is(true));
            assertThat("Client Scope contains 'scope3':", clientScopes2.containsKey("scope3"), Is.is(true));

            // Remove some binding and check it was removed
            client.removeClientScope(scope1);
            client.removeClientScope(scope2);
            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel client = realm.getClientByClientId("templatized");
            ClientScopeModel scope3 = scope3Atomic.get();

            Map<String, ClientScopeModel> clientScopes1 = client.getClientScopes(true);
            assertThat("Client Scope contains 'scope1':", clientScopes1.containsKey("scope1"), Is.is(false));
            assertThat("Client Scope contains 'scope2':", clientScopes1.containsKey("scope2"), Is.is(false));
            assertThat("Client Scope contains 'scope3':", clientScopes1.containsKey("scope3"), Is.is(false));

            Map<String, ClientScopeModel> clientScopes2 = client.getClientScopes(false);
            assertThat("Client Scope contains 'scope1':", clientScopes2.containsKey("scope1"), Is.is(false));
            assertThat("Client Scope contains 'scope2':", clientScopes2.containsKey("scope2"), Is.is(false));
            assertThat("Client Scope contains 'scope3':", clientScopes2.containsKey("scope3"), Is.is(true));

            session.clients().removeClient(realm, client.getId());
            client.removeClientScope(scope3);
            realm.removeClientScope(scope1Atomic.get().getId());
            realm.removeClientScope(scope2Atomic.get().getId());
            realm.removeClientScope(scope3Atomic.get().getId());
            return null;
        });
    }

    @Test
    public void testDefaultDefaultClientScopes() {
        AtomicReference<ClientScopeModel> scope1Atomic = new AtomicReference<>();
        AtomicReference<ClientScopeModel> scope2Atomic = new AtomicReference<>();
        AtomicReference<ClientScopeModel> scope3Atomic = new AtomicReference<>();

        withRealm(realmId, (session, realm) -> {
            ClientScopeModel scope1 = realm.addClientScope("scope1");
            scope1.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
            scope1Atomic.set(scope1);

            ClientScopeModel scope2 = realm.addClientScope("scope2");
            scope2.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
            scope2Atomic.set(scope2);

            ClientScopeModel scope3 = realm.addClientScope("scope3");
            scope3.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
            scope3Atomic.set(scope3);
            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientScopeModel scope1 = scope1Atomic.get();
            ClientScopeModel scope2 = scope2Atomic.get();
            ClientScopeModel scope3 = scope3Atomic.get();

            scope1 = realm.getClientScopeById(scope1.getId());
            scope2 = realm.getClientScopeById(scope2.getId());
            scope3 = realm.getClientScopeById(scope3.getId());

            realm.addDefaultClientScope(scope1, true);
            realm.addDefaultClientScope(scope2, false);
            realm.addDefaultClientScope(scope3, false);
            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel client = realm.addClient("foo");
            client.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel client = realm.getClientByClientId("foo");

            ClientScopeModel scope1 = scope1Atomic.get();
            ClientScopeModel scope2 = scope2Atomic.get();


            Map<String, ClientScopeModel> clientScopes1 = client.getClientScopes(true);
            assertThat("Client Scope contains 'scope1':", clientScopes1.containsKey("scope1"), Is.is(true));
            assertThat("Client Scope contains 'scope2':", clientScopes1.containsKey("scope2"), Is.is(false));
            assertThat("Client Scope contains 'scope3':", clientScopes1.containsKey("scope3"), Is.is(false));


            Map<String, ClientScopeModel> clientScopes2 = client.getClientScopes(false);
            assertThat("Client Scope contains 'scope1':", clientScopes2.containsKey("scope1"), Is.is(false));
            assertThat("Client Scope contains 'scope2':", clientScopes2.containsKey("scope2"), Is.is(true));
            assertThat("Client Scope contains 'scope3':", clientScopes2.containsKey("scope3"), Is.is(true));

            session.clients().removeClient(realm, client.getId());
            // Remove some realm default client scopes
            realm.removeDefaultClientScope(scope1);
            realm.removeDefaultClientScope(scope2);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel client = realm.addClient("foo2");
            client.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel client = realm.getClientByClientId("foo2");

            Map<String, ClientScopeModel> clientScopes1 = client.getClientScopes(true);
            assertThat("Client Scope contains 'scope1':", clientScopes1.containsKey("scope1"), Is.is(false));
            assertThat("Client Scope contains 'scope2':", clientScopes1.containsKey("scope2"), Is.is(false));
            assertThat("Client Scope contains 'scope3':", clientScopes1.containsKey("scope3"), Is.is(false));

            Map<String, ClientScopeModel> clientScopes2 = client.getClientScopes(false);
            assertThat("Client Scope contains 'scope1':", clientScopes2.containsKey("scope1"), Is.is(false));
            assertThat("Client Scope contains 'scope2':", clientScopes2.containsKey("scope2"), Is.is(false));
            assertThat("Client Scope contains 'scope3':", clientScopes2.containsKey("scope3"), Is.is(true));

            session.clients().removeClient(realm, client.getId());
            realm.removeClientScope(scope1Atomic.get().getId());
            realm.removeClientScope(scope2Atomic.get().getId());

            realm.removeDefaultClientScope(scope3Atomic.get());
            realm.removeClientScope(scope3Atomic.get().getId());
            return null;
        });
    }
}
