/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;

/**
 * Ported from
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class UserConsentModelTest extends KeycloakModelTest {

    private String realmId;

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().createRealm("original");
        realm.setDefaultRole(
                s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));

        ClientModel fooClient = realm.addClient("foo-client");
        ClientModel barClient = realm.addClient("bar-client");

        ClientScopeModel fooScope = realm.addClientScope("foo");
        fooScope.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);

        ClientScopeModel barScope = realm.addClientScope("bar");
        barScope.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);

        UserModel john = s.users().addUser(realm, "john");
        UserModel mary = s.users().addUser(realm, "mary");

        UserConsentModel johnFooGrant = new UserConsentModel(fooClient);
        johnFooGrant.addGrantedClientScope(fooScope);
        s.users().addConsent(realm, john.getId(), johnFooGrant);

        UserConsentModel johnBarGrant = new UserConsentModel(barClient);
        johnBarGrant.addGrantedClientScope(barScope);
        s.users().addConsent(realm, john.getId(), johnBarGrant);

        UserConsentModel maryFooGrant = new UserConsentModel(fooClient);
        maryFooGrant.addGrantedClientScope(fooScope);
        s.users().addConsent(realm, mary.getId(), maryFooGrant);

        // hardcoded-client was originally an added component to the realm
        ClientModel hardcodedClient = realm.addClient("hardcoded-client");

        UserConsentModel maryHardcodedGrant = new UserConsentModel(hardcodedClient);
        s.users().addConsent(realm, mary.getId(), maryHardcodedGrant);

        realmId = realm.getId();
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        s.realms().removeRealm(realmId);
    }

    private boolean isClientScopeGranted(RealmModel realm, String scopeName, UserConsentModel consentModel) {
        ClientScopeModel clientScope = KeycloakModelUtils.getClientScopeByName(realm, scopeName);
        return consentModel.isClientScopeGranted(clientScope);
    }

    @Test
    public void basicConsentTest() {
        withRealm(realmId, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            ClientModel barClient = realm.getClientByClientId("bar-client");

            UserModel john = session.users().getUserByUsername(realm, "john");
            UserModel mary = session.users().getUserByUsername(realm, "mary");

            UserConsentModel johnFooConsent =
                    session.users().getConsentByClient(realm, john.getId(), fooClient.getId());
            Assert.assertEquals(1, johnFooConsent.getGrantedClientScopes().size());
            Assert.assertTrue(isClientScopeGranted(realm, "foo", johnFooConsent));
            Assert.assertNotNull("Created Date should be set", johnFooConsent.getCreatedDate());
            Assert.assertNotNull("Last Updated Date should be set", johnFooConsent.getLastUpdatedDate());

            UserConsentModel johnBarConsent =
                    session.users().getConsentByClient(realm, john.getId(), barClient.getId());
            Assert.assertEquals(1, johnBarConsent.getGrantedClientScopes().size());
            Assert.assertTrue(isClientScopeGranted(realm, "bar", johnBarConsent));
            Assert.assertNotNull("Created Date should be set", johnBarConsent.getCreatedDate());
            Assert.assertNotNull("Last Updated Date should be set", johnBarConsent.getLastUpdatedDate());

            UserConsentModel maryConsent = session.users().getConsentByClient(realm, mary.getId(), fooClient.getId());
            Assert.assertEquals(1, maryConsent.getGrantedClientScopes().size());
            Assert.assertTrue(isClientScopeGranted(realm, "foo", maryConsent));
            Assert.assertNotNull("Created Date should be set", maryConsent.getCreatedDate());
            Assert.assertNotNull("Last Updated Date should be set", maryConsent.getLastUpdatedDate());

            ClientModel hardcodedClient = session.clients().getClientByClientId(realm, "hardcoded-client");
            UserConsentModel maryHardcodedConsent =
                    session.users().getConsentByClient(realm, mary.getId(), hardcodedClient.getId());
            Assert.assertEquals(0, maryHardcodedConsent.getGrantedClientScopes().size());
            Assert.assertNotNull("Created Date should be set", maryHardcodedConsent.getCreatedDate());
            Assert.assertNotNull("Last Updated Date should be set", maryHardcodedConsent.getLastUpdatedDate());

            Assert.assertNull(session.users().getConsentByClient(realm, john.getId(), hardcodedClient.getId()));

            Assert.assertNull(session.users().getConsentByClient(realm, mary.getId(), barClient.getId()));

            return null;
        });
    }

    @Test
    public void getAllConsentTest() {
        withRealm(realmId, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");

            UserModel john = session.users().getUserByUsername(realm, "john");
            UserModel mary = session.users().getUserByUsername(realm, "mary");

            Assert.assertEquals(
                    2, session.users().getConsentsStream(realm, john.getId()).count());

            ClientModel hardcodedClient = session.clients().getClientByClientId(realm, "hardcoded-client");

            List<UserConsentModel> maryConsents =
                    session.users().getConsentsStream(realm, mary.getId()).collect(Collectors.toList());
            Assert.assertEquals(2, maryConsents.size());
            UserConsentModel maryConsent = maryConsents.get(0);
            UserConsentModel maryHardcodedConsent = maryConsents.get(1);
            if (maryConsents.get(0).getClient().getId().equals(hardcodedClient.getId())) {
                maryConsent = maryConsents.get(1);
                maryHardcodedConsent = maryConsents.get(0);
            }
            Assert.assertEquals(fooClient.getId(), maryConsent.getClient().getId());
            Assert.assertEquals(1, maryConsent.getGrantedClientScopes().size());
            Assert.assertTrue(isClientScopeGranted(realm, "foo", maryConsent));

            Assert.assertEquals(
                    hardcodedClient.getId(), maryHardcodedConsent.getClient().getId());
            Assert.assertEquals(0, maryHardcodedConsent.getGrantedClientScopes().size());

            return null;
        });
    }

    @Test
    public void updateWithClientScopeRemovalTest() {
        withRealm(realmId, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            UserModel john = session.users().getUserByUsername(realm, "john");

            UserConsentModel johnConsent = session.users().getConsentByClient(realm, john.getId(), fooClient.getId());
            Assert.assertEquals(1, johnConsent.getGrantedClientScopes().size());

            // Remove foo protocol mapper from johnConsent
            ClientScopeModel fooScope = KeycloakModelUtils.getClientScopeByName(realm, "foo");
            johnConsent.getGrantedClientScopes().remove(fooScope);

            session.users().updateConsent(realm, john.getId(), johnConsent);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            UserModel john = session.users().getUserByUsername(realm, "john");
            UserConsentModel johnConsent = session.users().getConsentByClient(realm, john.getId(), fooClient.getId());

            Assert.assertEquals(0, johnConsent.getGrantedClientScopes().size());
            Assert.assertTrue(
                    "Created date should be less than last updated date",
                    johnConsent.getCreatedDate() < johnConsent.getLastUpdatedDate());

            return null;
        });
    }

    @Test
    public void revokeTest() {
        withRealm(realmId, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            UserModel john = session.users().getUserByUsername(realm, "john");
            UserModel mary = session.users().getUserByUsername(realm, "mary");

            session.users().revokeConsentForClient(realm, john.getId(), fooClient.getId());
            ClientModel hardcodedClient = session.clients().getClientByClientId(realm, "hardcoded-client");
            session.users().revokeConsentForClient(realm, mary.getId(), hardcodedClient.getId());

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            ClientModel hardcodedClient = session.clients().getClientByClientId(realm, "hardcoded-client");

            UserModel john = session.users().getUserByUsername(realm, "john");
            Assert.assertNull(session.users().getConsentByClient(realm, john.getId(), fooClient.getId()));
            UserModel mary = session.users().getUserByUsername(realm, "mary");
            Assert.assertNull(session.users().getConsentByClient(realm, mary.getId(), hardcodedClient.getId()));

            return null;
        });
    }

    @Test
    public void deleteUserTest() {
        AtomicReference<String> johnUserID = new AtomicReference<>();

        withRealm(realmId, (session, realm) -> {
            UserModel john = session.users().getUserByUsername(realm, "john");
            johnUserID.set(john.getId());
            session.users().removeUser(realm, john);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            Assert.assertEquals(
                    0,
                    session.users().getConsentsStream(realm, johnUserID.get()).count());

            UserModel mary = session.users().getUserByUsername(realm, "mary");
            Assert.assertEquals(
                    2, session.users().getConsentsStream(realm, mary.getId()).count());

            return null;
        });
    }

    @Test
    public void deleteClientScopeTest() {
        withRealm(realmId, (session, realm) -> {
            ClientScopeModel fooScope = KeycloakModelUtils.getClientScopeByName(realm, "foo");
            realm.removeClientScope(fooScope.getId());

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");

            UserModel john = session.users().getUserByUsername(realm, "john");
            UserConsentModel johnConsent = session.users().getConsentByClient(realm, john.getId(), fooClient.getId());

            Assert.assertEquals(0, johnConsent.getGrantedClientScopes().size());

            return null;
        });
    }

    @Test
    public void deleteClientTest() {
        AtomicReference<String> barClientID = new AtomicReference<>();

        withRealm(realmId, (session, realm) -> {
            ClientModel barClient = realm.getClientByClientId("bar-client");
            barClientID.set(barClient.getId());

            realm.removeClient(barClient.getId());
            Assert.assertNull(realm.getClientByClientId("bar-client"));

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            UserModel john = session.users().getUserByUsername(realm, "john");

            UserConsentModel johnFooConsent =
                    session.users().getConsentByClient(realm, john.getId(), fooClient.getId());
            Assert.assertEquals(1, johnFooConsent.getGrantedClientScopes().size());
            Assert.assertTrue(isClientScopeGranted(realm, "foo", johnFooConsent));

            Assert.assertNull(session.users().getConsentByClient(realm, john.getId(), barClientID.get()));

            return null;
        });
    }
}
