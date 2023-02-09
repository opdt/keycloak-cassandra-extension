/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
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

package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import org.junit.Test;
import org.keycloak.common.constants.KerberosConstants;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.OIDCLoginProtocolFactory;
import org.keycloak.protocol.oidc.mappers.UserPropertyMapper;
import org.keycloak.protocol.oidc.mappers.UserSessionNoteMapper;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RequireProvider(RealmProvider.class)
@RequireProvider(ClientProvider.class)
@RequireProvider(ClientScopeProvider.class)
public class ClientScopeModelTest extends KeycloakModelTest {

    private String realmId;


    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().createRealm("realm");
        this.realmId = realm.getId();
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        s.realms().removeRealm(realmId);
    }

    @Test
    public void testBasicAttributes() {
        withRealm(realmId, (session, realm) -> {
            ClientScopeModel clientScope = session.clientScopes().addClientScope(realm, "myClientScope1");

            clientScope.setName("Testscope");
            clientScope.setDescription("Desc");
            clientScope.setIsDynamicScope(false);
            clientScope.setProtocol("openid-connect");
            clientScope.setAttribute("testKey", "testVal");

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            List<String> clientScopes = session.clientScopes().getClientScopesStream(realm)
                .map(ClientScopeModel::getId)
                .collect(Collectors.toList());
            assertThat(clientScopes, hasSize(1));

            ClientScopeModel clientScope = session.clientScopes().getClientScopeById(realm, clientScopes.get(0));
            assertThat(clientScope.getName(), is("Testscope"));
            assertThat(clientScope.getDescription(), is("Desc"));
            assertThat(clientScope.isDynamicScope(), is(false));
            assertThat(clientScope.getProtocol(), is("openid-connect"));
            assertThat(clientScope.getAttribute("testKey"), is("testVal"));
            assertThat(clientScope.getAttributes().get("testKey"), is("testVal"));

            session.clientScopes().removeClientScope(realm, clientScopes.get(0));

            return null;
        });
    }

    @Test
    public void testProtocolMappers() {
        ProtocolMapperModel usernameMapper = UserPropertyMapper.createClaimMapper(OIDCLoginProtocolFactory.USERNAME,
            "username",
            "preferred_username", "String",
            true, true);

        ProtocolMapperModel kerberosMapper = UserSessionNoteMapper.createClaimMapper(KerberosConstants.GSS_DELEGATION_CREDENTIAL_DISPLAY_NAME,
            KerberosConstants.GSS_DELEGATION_CREDENTIAL,
            KerberosConstants.GSS_DELEGATION_CREDENTIAL, "String",
            true, false);

        withRealm(realmId, (session, realm) -> {

            ClientScopeModel clientScope = session.clientScopes().addClientScope(realm, "myClientScope1");
            clientScope.addProtocolMapper(usernameMapper);
            clientScope.addProtocolMapper(kerberosMapper);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            List<String> clientScopes = session.clientScopes().getClientScopesStream(realm)
                .map(ClientScopeModel::getId)
                .collect(Collectors.toList());
            assertThat(clientScopes, hasSize(1));

            ClientScopeModel clientScope = session.clientScopes().getClientScopeById(realm, clientScopes.get(0));

            ProtocolMapperModel actualUsernameMapper = clientScope.getProtocolMapperByName("openid-connect", OIDCLoginProtocolFactory.USERNAME);
            assertThat(actualUsernameMapper, is(usernameMapper));

            ProtocolMapperModel actualKerberosMapper = clientScope.getProtocolMapperByName("openid-connect", KerberosConstants.GSS_DELEGATION_CREDENTIAL_DISPLAY_NAME);
            assertThat(actualKerberosMapper, is(kerberosMapper));

            assertThat(clientScope.getProtocolMapperByName("saml", OIDCLoginProtocolFactory.USERNAME), nullValue());
            assertThat(clientScope.getProtocolMapperById(actualUsernameMapper.getId()), is(usernameMapper));

            ProtocolMapperModel updatedUsernameMapper = UserPropertyMapper.createClaimMapper(OIDCLoginProtocolFactory.USERNAME,
                "username",
                "preferred_username_updated", "String",
                true, true);

            clientScope.updateProtocolMapper(updatedUsernameMapper);
            assertThat(clientScope.getProtocolMapperByName("openid-connect", OIDCLoginProtocolFactory.USERNAME), is(updatedUsernameMapper));

            clientScope.removeProtocolMapper(updatedUsernameMapper);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            List<String> clientScopes = session.clientScopes().getClientScopesStream(realm)
                .map(ClientScopeModel::getId)
                .collect(Collectors.toList());

            ClientScopeModel clientScope = session.clientScopes().getClientScopeById(realm, clientScopes.get(0));

            assertThat(clientScope.getProtocolMapperByName("openid-connect", OIDCLoginProtocolFactory.USERNAME), nullValue());
            assertThat(clientScope.getProtocolMapperByName("openid-connect", KerberosConstants.GSS_DELEGATION_CREDENTIAL_DISPLAY_NAME), is(kerberosMapper));

            session.clientScopes().removeClientScope(realm, clientScopes.get(0));
            return null;
        });
    }

    @Test
    public void testScopeMappings() {
        ClientModel client = withRealm(realmId, (session, realm) -> session.clients().addClient(realm, "myClient"));
        RoleModel realmRole = withRealm(realmId, (session, realm) -> realm.addRole("realmRole"));
        RoleModel clientRole = withRealm(realmId, (session, realm) -> client.addRole("clientRole"));

        withRealm(realmId, (session, realm) -> {
            ClientScopeModel clientScope = session.clientScopes().addClientScope(realm, "myClientScope1");

            clientScope.addScopeMapping(realmRole);
            clientScope.addScopeMapping(clientRole);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            List<String> clientScopes = session.clientScopes().getClientScopesStream(realm)
                .map(ClientScopeModel::getId)
                .collect(Collectors.toList());
            assertThat(clientScopes, hasSize(1));

            ClientScopeModel clientScope = session.clientScopes().getClientScopeById(realm, clientScopes.get(0));
            List<RoleModel> scopeMappings = clientScope.getScopeMappingsStream().collect(Collectors.toList());
            assertThat(scopeMappings, hasSize(2));
            assertThat(scopeMappings.stream().map(RoleModel::getName).collect(Collectors.toList()), containsInAnyOrder("realmRole", "clientRole"));

            List<RoleModel> realmScopeMappings = clientScope.getRealmScopeMappingsStream().collect(Collectors.toList());
            assertThat(realmScopeMappings, hasSize(1));
            assertThat(realmScopeMappings.get(0).getName(), is("realmRole"));

            assertThat(clientScope.hasScope(realmRole), is(true));
            assertThat(clientScope.hasScope(clientRole), is(true));

            clientScope.deleteScopeMapping(realmRole);
            assertThat(clientScope.hasScope(realmRole), is(false));
            assertThat(clientScope.getRealmScopeMappingsStream().collect(Collectors.toList()), hasSize(0));
            assertThat(clientScope.getScopeMappingsStream().collect(Collectors.toList()), hasSize(1));

            session.clientScopes().removeClientScope(realm, clientScopes.get(0));

            return null;
        });
    }
}
