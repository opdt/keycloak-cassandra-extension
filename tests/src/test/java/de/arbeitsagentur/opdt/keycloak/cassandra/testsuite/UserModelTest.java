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

import de.arbeitsagentur.opdt.keycloak.cassandra.user.CassandraUserAdapter;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.models.*;
import org.keycloak.services.managers.ClientManager;
import org.keycloak.services.managers.RealmManager;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * Ported from:
 *
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */

@RequireProvider(UserProvider.class)
@RequireProvider(RealmProvider.class)
public class UserModelTest extends KeycloakModelTest {

    protected static final int NUM_GROUPS = 100;
    private String originalRealmId;
    private String otherRealmId;
    private String realm1RealmId;
    private String realm2RealmId;
    private final List<String> groupIds = new ArrayList<>(NUM_GROUPS);
    @Override
    protected boolean isUseSameKeycloakSessionFactoryForAllThreads() {
        return true;
    }
    @Override
    public void createEnvironment(KeycloakSession s) {
        originalRealmId = s.realms().createRealm("original").getId();
        otherRealmId = s.realms().createRealm("other").getId();
        realm1RealmId = s.realms().createRealm("realm1").getId();
        realm2RealmId = s.realms().createRealm("realm2").getId();

        IntStream.range(0, NUM_GROUPS).forEach(i -> {
            groupIds.add(s.groups().createGroup(s.realms().getRealm(originalRealmId), "group-" + i).getId());
        });
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        s.realms().removeRealm(originalRealmId);
        s.realms().removeRealm(otherRealmId);
        s.realms().removeRealm(realm1RealmId);
        s.realms().removeRealm(realm2RealmId);
    }

    @Test
    public void staleUserUpdate() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setFirstName("first-name");
            user.setLastName("last-name");
            user.setEmail("email");

            return null;
        });

        boolean staleExceptionOccured = false;
        try {
            withRealm(originalRealmId, (session, realm) -> {
                UserModel user = session.users().getUserByUsername(realm, "user");
                assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("2"));

                user.setAttribute(CassandraUserAdapter.ENTITY_VERSION, Arrays.asList("1"));

                return null;
            });
        } catch (Exception e) {
            staleExceptionOccured = true;
        }

        assertTrue(staleExceptionOccured);

        staleExceptionOccured = false;
        try {
            withRealm(originalRealmId, (session, realm) -> {
                UserModel user = session.users().getUserByUsername(realm, "user");
                assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("2"));

                user.setAttribute(CassandraUserAdapter.ENTITY_VERSION, Arrays.asList("3"));

                return null;
            });
        } catch (Exception e) {
            staleExceptionOccured = true;
        }

        assertTrue(staleExceptionOccured);
    }

    @Test
    public void testWorkingUserUpdate() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setFirstName("first-name");
            user.setLastName("last-name");
            user.setEmail("email");

            return null;
        });

        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("2"));

            user.setUsername("updateduser");
            user.setSingleAttribute("test", "bla");

            return null;
        });

        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "updateduser");

            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("3"));
            assertThat(user.getFirstAttribute("test"), is("bla"));

            assertNull(session.users().getUserByUsername(realm, null));

            return null;
        });
    }

    @Test
    public void testUsernameEqualsMailNoConfilctNoThrow() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            bob.setUsername("newmail@bob");
            assertEquals("newmail@bob", bob.getUsername());

            return null;
        });
    }

    @Test
    public void testUsernameEqualsMailNoThrowIfUniquenessAcrossBothNotEnforced() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, false);
            realm.setDuplicateEmailsAllowed(false);
            bob.setUsername(alice.getEmail());
            assertEquals(bob.getUsername(), alice.getEmail());

            return null;
        });
    }

    @Test
    public void testUsernameEqualsMailNoThrowIfDuplicateMailsAllowed() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(true);
            bob.setUsername(alice.getEmail());
            assertEquals(bob.getUsername(), alice.getEmail());

            return null;
        });
    }

    @Test
    public void testUsernameEqualsMailNoThrowForOwnMail() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            bob.setUsername(bob.getEmail());
            assertEquals(bob.getUsername(), bob.getEmail());

            return null;
        });
    }

    @SuppressWarnings("java:S5778")
    @Test
    public void testUsernameEqualsMailThrowIfChecksEnabledAndUsedMail() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            assertThrows(ModelDuplicateException.class, () -> bob.setUsername(alice.getEmail()));

            return null;
        });
    }

    @Test
    public void testMailEqualsUsernameNoConfilctNoThrow() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "info@alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "info@bob");
            bob.setEmail("info@bob");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            bob.setEmail("newmail@bob");
            assertEquals("newmail@bob", bob.getEmail());

            return null;
        });
    }

    @Test
    public void testMailEqualsUsernameNoThrowIfUniquenessAcrossBothNotEnforced() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "info@alice");
            alice.setEmail("othermail@alice"); // else the duplicate-mail check would interfere
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, false);
            realm.setDuplicateEmailsAllowed(false);
            bob.setEmail(alice.getUsername());
            assertEquals(bob.getEmail(), alice.getUsername());

            return null;
        });
    }

    @Test
    public void testMailEqualsUsernameNoThrowIfDuplicateMailsAllowed() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "info@alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "info@bob");
            bob.setEmail("info@bob");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(true);
            bob.setEmail(alice.getUsername());
            assertEquals(bob.getEmail(), alice.getUsername());

            return null;
        });
    }

    @Test
    public void testMailEqualsUsernameNoThrowForOwnUsername() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel bob = session.users().addUser(realm, "info@bob");
            bob.setEmail("othermail@bob");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            bob.setEmail(bob.getUsername());
            assertEquals(bob.getUsername(), bob.getEmail());

            return null;
        });
    }

    @SuppressWarnings("java:S5778")
    @Test
    public void testMailEqualsUsernameThrowIfChecksEnabledAndExistingUsername() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "info@alice");
            alice.setEmail("othermail@alice"); // else the duplicate-mail check would interfere
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            assertThrows(ModelDuplicateException.class, () -> bob.setEmail(alice.getUsername()));

            return null;
        });
    }


    @Test
    public void testSetEntityVersion() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("1"));
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION_READONLY), is("1"));

            user.setSingleAttribute(CassandraUserAdapter.ENTITY_VERSION_READONLY, "42");
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("1"));
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION_READONLY), is("1"));

            user.setSingleAttribute(CassandraUserAdapter.ENTITY_VERSION, "2");
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("2"));
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION_READONLY), is("2"));

            user.setSingleAttribute(CassandraUserAdapter.ENTITY_VERSION, "1");
            return null;
        });

        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("2"));
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION_READONLY), is("2"));

            return null;
        });
    }

    @Test
    public void testPersistUser() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setFirstName("first-name");
            user.setLastName("last-name");
            user.setEmail("email");
            assertNotNull(user.getCreatedTimestamp());
            // test that timestamp is current with 10s tollerance
            assertTrue((System.currentTimeMillis() - user.getCreatedTimestamp()) < 10000);

            user.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
            user.addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);

            RealmModel searchRealm = session.realms().getRealm(realm.getId());
            UserModel persisted = session.users().getUserByUsername(searchRealm, "user");

            assertUserModel(user, persisted);

            searchRealm = session.realms().getRealm(realm.getId());
            UserModel persisted2 = session.users().getUserById(searchRealm, user.getId());
            assertUserModel(user, persisted2);

            Map<String, String> attributes = new HashMap<>();
            attributes.put(UserModel.EMAIL, "email");
            List<UserModel> search = session.users().searchForUserStream(realm, attributes)
                .collect(Collectors.toList());
            Assert.assertThat(search, hasSize(1));
            Assert.assertThat(search.get(0).getUsername(), equalTo("user"));

            return null;
        });
    }

    @Test
    public void testWebOriginSet() {
        withRealm(originalRealmId, (session, realm) -> {
            ClientModel client = realm.addClient("user");

            Assert.assertThat(client.getWebOrigins(), empty());

            client.addWebOrigin("origin-1");
            Assert.assertThat(client.getWebOrigins(), hasSize(1));

            client.addWebOrigin("origin-2");
            Assert.assertThat(client.getWebOrigins(), hasSize(2));

            client.removeWebOrigin("origin-2");
            Assert.assertThat(client.getWebOrigins(), hasSize(1));

            client.removeWebOrigin("origin-1");
            Assert.assertThat(client.getWebOrigins(), empty());

            client = realm.addClient("oauthclient2");

            Assert.assertThat(client.getWebOrigins(), empty());

            client.addWebOrigin("origin-1");
            Assert.assertThat(client.getWebOrigins(), hasSize(1));

            client.addWebOrigin("origin-2");
            Assert.assertThat(client.getWebOrigins(), hasSize(2));

            client.removeWebOrigin("origin-2");
            Assert.assertThat(client.getWebOrigins(), hasSize(1));

            client.removeWebOrigin("origin-1");
            Assert.assertThat(client.getWebOrigins(), empty());

            return null;
        });
    }

    @Test
    public void testUserRequiredActions() throws Exception {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            List<String> requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, empty());

            user.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
            String id = realm.getId();

            realm = session.realms().getRealm(id);
            user = session.users().getUserByUsername(realm, "user");

            requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, hasSize(1));
            Assert.assertThat(requiredActions, contains(UserModel.RequiredAction.CONFIGURE_TOTP.name()));

            user.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
            user = session.users().getUserByUsername(realm, "user");

            requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, hasSize(1));
            Assert.assertThat(requiredActions, contains(UserModel.RequiredAction.CONFIGURE_TOTP.name()));

            user.addRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL.name());
            user = session.users().getUserByUsername(realm, "user");

            requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, hasSize(2));
            Assert.assertThat(requiredActions, containsInAnyOrder(
                UserModel.RequiredAction.CONFIGURE_TOTP.name(),
                UserModel.RequiredAction.VERIFY_EMAIL.name())
            );

            user.removeRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP.name());
            user = session.users().getUserByUsername(realm, "user");

            requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, hasSize(1));
            Assert.assertThat(requiredActions, contains(UserModel.RequiredAction.VERIFY_EMAIL.name()));

            user.removeRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL.name());
            user = session.users().getUserByUsername(realm, "user");

            requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, empty());

            return null;
        });
    }

    @Test
    public void testUserMultipleAttributes() throws Exception {
        AtomicReference<List<String>> attrValsAtomic = new AtomicReference<>();

        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            session.users().addUser(realm, "user-noattrs");

            user.setSingleAttribute("key1", "value1");

            List<String> attrVals = new ArrayList<>(Arrays.asList("val21", "val22"));
            attrValsAtomic.set(attrVals);

            user.setAttribute("key2", attrVals);

            return null;
        });

        withRealm(originalRealmId, (session, realm) -> {
            // Test read attributes
            UserModel user = session.users().getUserByUsername(realm, "user");

            List<String> attrVals = user.getAttributeStream("key1").collect(Collectors.toList());
            Assert.assertThat(attrVals, hasSize(1));
            Assert.assertThat(attrVals, contains("value1"));
            Assert.assertThat(user.getFirstAttribute("key1"), equalTo("value1"));

            attrVals = user.getAttributeStream("key2").collect(Collectors.toList());
            Assert.assertThat(attrVals, hasSize(2));
            Assert.assertThat(attrVals, containsInAnyOrder("val21", "val22"));

            attrVals = user.getAttributeStream("key3").collect(Collectors.toList());
            Assert.assertThat(attrVals, empty());
            Assert.assertThat(user.getFirstAttribute("key3"), nullValue());

            Map<String, List<String>> allAttrVals = user.getAttributes();
            Assert.assertThat(allAttrVals.keySet(), hasSize(7));
            Assert.assertThat(allAttrVals.keySet(), containsInAnyOrder(CassandraUserAdapter.ENTITY_VERSION_READONLY, UserModel.USERNAME, UserModel.FIRST_NAME, UserModel.LAST_NAME, UserModel.EMAIL, "key1", "key2"));
            Assert.assertThat(allAttrVals.get("key1"), equalTo(user.getAttributeStream("key1").collect(Collectors.toList())));
            Assert.assertThat(allAttrVals.get("key2"), equalTo(user.getAttributeStream("key2").collect(Collectors.toList())));

            // Test remove and rewrite attribute
            user.removeAttribute("key1");
            user.setSingleAttribute("key2", "val23");

            return null;
        });

        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            Assert.assertThat(user.getFirstAttribute("key1"), nullValue());

            List<String> attrVals = user.getAttributeStream("key2").collect(Collectors.toList());

            Assert.assertThat(attrVals, hasSize(1));
            Assert.assertThat(attrVals.get(0), equalTo("val23"));

            return null;
        });
    }

    @Test
    public void testUpdateUserAttribute() throws Exception {

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user = currentSession.users().addUser(realm, "user");

            user.setSingleAttribute("key1", "value1");
            user.setSingleAttribute(UserModel.USERNAME, "userUpdated");
            user.setSingleAttribute(UserModel.FIRST_NAME, "fn");
            user.setSingleAttribute(UserModel.LAST_NAME, "ln");
            user.setSingleAttribute(UserModel.EMAIL, "e@f.de");
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "userUpdated");

            assertThat(user.getFirstName(), is("fn"));
            assertThat(user.getLastName(), is("ln"));
            assertThat(user.getEmail(), is("e@f.de"));

            // Update attribute
            List<String> attrVals = new ArrayList<>(Arrays.asList("val2"));
            user.setAttribute("key1", attrVals);
            Map<String, List<String>> allAttrVals = user.getAttributes();

            // Ensure same transaction is able to see updated value
            Assert.assertThat(allAttrVals.keySet(), hasSize(6));
            Assert.assertThat(allAttrVals.keySet(), containsInAnyOrder("key1", CassandraUserAdapter.ENTITY_VERSION_READONLY, UserModel.FIRST_NAME, UserModel.LAST_NAME, UserModel.EMAIL, UserModel.USERNAME));
            Assert.assertThat(allAttrVals.get("key1"), contains("val2"));
            return null;
        });
    }

    // KEYCLOAK-3608
    @Test
    public void testUpdateUserSingleAttribute() {

        AtomicReference<Map<String, List<String>>> expectedAtomic = new AtomicReference<>();

        withRealm(originalRealmId, (currentSession, realm) -> {
            Map<String, List<String>> expected = new HashMap<>();
            expected.put("key1", Collections.singletonList("value3"));
            expected.put("key2", Collections.singletonList("value2"));
            expected.put(UserModel.FIRST_NAME, Collections.singletonList(null));
            expected.put(UserModel.LAST_NAME, Collections.singletonList(null));
            expected.put(UserModel.EMAIL, Collections.singletonList(null));
            expected.put(UserModel.USERNAME, Collections.singletonList("user"));
            expected.put(CassandraUserAdapter.ENTITY_VERSION_READONLY, Collections.singletonList("1"));

            UserModel user = currentSession.users().addUser(realm, "user");

            user.setSingleAttribute("key1", "value1");
            user.setSingleAttribute("key2", "value2");
            user.setSingleAttribute("key3", null); //KEYCLOAK-7014

            // Overwrite the first attribute
            user.setSingleAttribute("key1", "value3");

            Assert.assertThat(user.getAttributes(), equalTo(expected));

            expectedAtomic.set(expected);
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            Map<String, List<String>> expected = expectedAtomic.get();
            expected.put(CassandraUserAdapter.ENTITY_VERSION_READONLY, Collections.singletonList("2"));

            Assert.assertThat(currentSession.users().getUserByUsername(realm, "user").getAttributes(), equalTo(expected));
            return null;
        });
    }

    @Test
    public void testSearchByString() {

        withRealm(originalRealmId, (currentSession, realm) -> {
            currentSession.users().addUser(realm, "user1");
            currentSession.users().addUser(realm, "user2");
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");

            List<UserModel> users = currentSession.users().searchForUserStream(realm, "user1", 0, 7)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user1));
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            List<UserModel> users = currentSession.users().searchForUserStream(realm, (String) null, null, null)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user2));
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            List<UserModel> users = currentSession.users().searchForUserStream(realm, "", 0, 7)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user2));
            return null;
        });
    }



    @Test
    public void testSearchByParams() {

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().addUser(realm, "user1");
            user1.setSingleAttribute(UserModel.SearchableFields.IDP_AND_USER.getName(), null);

            UserModel user2 = currentSession.users().addUser(realm, "user2");
            user2.setEmail("user2@example.com");
            user2.setSingleAttribute(UserModel.SearchableFields.IDP_AND_USER.getName(), "fakeIDPValue");

            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            realm.setAttribute("keycloak.username-search.case-sensitive", "false");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.USERNAME, "UsEr2");
            params.put(UserModel.EXACT, "true");
            List<UserModel> users = currentSession.users().searchForUserStream(realm, params, 0, 7)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            realm.setAttribute("keycloak.username-search.case-sensitive", "true");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.USERNAME, "UsEr2");
            params.put(UserModel.EXACT, "false");
            List<UserModel> users = currentSession.users().searchForUserStream(realm, params, 0, 7)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(0));
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            realm.setAttribute("keycloak.username-search.case-sensitive", "false");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.USERNAME, "UsEr2");
            params.put(UserModel.EXACT, "false");
            List<UserModel> users = currentSession.users().searchForUserStream(realm, params, 0, 7)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            realm.setAttribute("keycloak.username-search.case-sensitive", "true");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.USERNAME, "UsEr2");
            params.put(UserModel.EXACT, "true");
            List<UserModel> users = currentSession.users().searchForUserStream(realm, params, 0, 7)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(0));
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.EMAIL, "user2@example.com");
            params.put(UserModel.EXACT, "true");
            List<UserModel> users = currentSession.users().searchForUserStream(realm, params, 0, 7)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.SEARCH, "user2");
            params.put(UserModel.INCLUDE_SERVICE_ACCOUNT, "false");
            params.put(UserModel.IDP_ALIAS, "fakeIDPValue");
            List<UserModel> users = currentSession.users().searchForUserStream(realm, params, 0, 7)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.IDP_ALIAS, "fakeIDPValue");
            params.put(UserModel.IDP_USER_ID, "fakeIDPValue");
            List<UserModel> users = currentSession.users().searchForUserStream(realm, params, 0, 7)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.IDP_ALIAS, "fakeIDPValue");
            params.put(UserModel.IDP_USER_ID, "fakeIDPValue");
            List<UserModel> users = currentSession.users().searchForUserStream(realm, params, 0, 7)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            Map<String, String> params = new HashMap<>();
            params.put(UserModel.USERNAME, "user1");
            params.put(UserModel.INCLUDE_SERVICE_ACCOUNT, "true");
            params.put(UserModel.IDP_ALIAS, "fakeIDPValue");
            List<UserModel> users = currentSession.users().searchForUserStream(realm, params, 0, 7)
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(0));
            return null;
        });
    }

    @Test
    public void testSearchByUserAttribute() throws Exception {

        withRealm(originalRealmId, (currentSession, realm) -> {

            UserModel user1 = currentSession.users().addUser(realm, "user1");
            UserModel user2 = currentSession.users().addUser(realm, "user2");
            UserModel user3 = currentSession.users().addUser(realm, "user3");
            RealmModel otherRealm = currentSession.realms().getRealmByName("other");
            UserModel otherRealmUser = currentSession.users().addUser(otherRealm, "user1");

            user1.setSingleAttribute("key1", "value1");
            user1.setSingleAttribute("key2", "value21");

            user2.setSingleAttribute("key1", "value1");
            user2.setSingleAttribute("key2", "value22");

            user3.setSingleAttribute("key2", "value21");

            otherRealmUser.setSingleAttribute("key2", "value21");
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");
            UserModel user3 = currentSession.users().getUserByUsername(realm, "user3");

            List<UserModel> users = currentSession.users().searchForUserByUserAttributeStream(realm, "key1", "value1")
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user2));

            users = currentSession.users().searchForUserByUserAttributeStream(realm, "key2", "value21")
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user3));

            users = currentSession.users().searchForUserByUserAttributeStream(realm, "key2", "value22")
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));

            users = currentSession.users().searchForUserByUserAttributeStream(realm, "key3", "value3")
                .collect(Collectors.toList());
            Assert.assertThat(users, empty());
            return null;
        });
    }

    @Test
    public void testServiceAccountLink() throws Exception {

        withRealm(originalRealmId, (currentSession, realm) -> {
            ClientModel client = realm.addClient("foo");

            UserModel user1 = currentSession.users().addUser(realm, "user1");
            user1.setFirstName("John");
            user1.setLastName("Doe");
            user1.setSingleAttribute("indexed.fullName", "John Doe");

            UserModel user2 = currentSession.users().addUser(realm, "user2");
            user2.setFirstName("John");
            user2.setLastName("Doe");
            user2.setSingleAttribute("indexed.fullName", "John Doe");

            // Search
            Assert.assertThat(currentSession.users().getServiceAccount(client), nullValue());
            List<UserModel> users = currentSession.users().searchForUserByUserAttributeStream(realm, "indexed.fullName", "John Doe")
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user2));

            // Link service account
            user1.setServiceAccountClientLink(client.getId());

            UserModel searched = currentSession.users().getServiceAccount(client);
            Assert.assertThat(searched, equalTo(user1));
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            // Search and assert service account user not found
            ClientModel client = realm.getClientByClientId("foo");
            UserModel searched = currentSession.users().getServiceAccount(client);
            Assert.assertThat(searched, equalTo(user1));
            List<UserModel> users = currentSession.users().searchForUserByUserAttributeStream(realm, "indexed.fullName", "John Doe")
                .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));

            users = currentSession.users().searchForUserStream(realm, Collections.singletonMap(UserModel.INCLUDE_SERVICE_ACCOUNT, Boolean.FALSE.toString())).collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));

            users = currentSession.users().searchForUserStream(realm, Collections.emptyMap()).collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user2));

            Assert.assertThat(currentSession.users().getUsersCount(realm, true), equalTo(2));
            Assert.assertThat(currentSession.users().getUsersCount(realm, false), equalTo(1));

            // Remove client
            RealmManager realmMgr = new RealmManager(currentSession);
            ClientManager clientMgr = new ClientManager(realmMgr);

            clientMgr.removeClient(realm, client);
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            // Assert service account removed as well
            Assert.assertThat(currentSession.users().getUserByUsername(realm, "user1"), nullValue());
            return null;
        });
    }

    @Test
    public void testGrantToAll() throws Exception {

        withRealm(realm1RealmId, (currentSession, realm1) -> {

            realm1.addRole("role1");
            currentSession.users().addUser(realm1, "user1");
            currentSession.users().addUser(realm1, "user2");

            RealmModel realm2 = currentSession.realms().getRealmByName("realm2");
            currentSession.users().addUser(realm2, "user1");
            return null;
        });

        withRealm(realm1RealmId, (currentSession, realm1) -> {

            RoleModel role1 = realm1.getRole("role1");
            currentSession.users().grantToAllUsers(realm1, role1);
            return null;
        });

        withRealm(realm1RealmId, (currentSession, realm1) -> {
            RoleModel role1 = realm1.getRole("role1");
            UserModel user1 = currentSession.users().getUserByUsername(realm1, "user1");
            UserModel user2 = currentSession.users().getUserByUsername(realm1, "user2");
            assertTrue(user1.hasRole(role1));
            assertTrue(user2.hasRole(role1));

            RealmModel realm2 = currentSession.realms().getRealmByName("realm2");
            UserModel realm2User1 = currentSession.users().getUserByUsername(realm2, "user1");
            assertFalse(realm2User1.hasRole(role1));

            user1.deleteRoleMapping(role1);
            return null;
        });

        withRealm(realm1RealmId, (currentSession, realm1) -> {
            RealmModel realm2 = currentSession.realms().getRealmByName("realm2");
            RoleModel role1 = realm1.getRole("role1");
            UserModel user1 = currentSession.users().getUserByUsername(realm1, "user1");
            assertFalse(user1.hasRole(role1));

            currentSession.realms().removeRealm(realm1.getId());
            currentSession.realms().removeRealm(realm2.getId());
            return null;
        });
    }

    @Test
    public void testUserNotBefore() throws Exception {

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().addUser(realm, "user1");
            currentSession.users().setNotBeforeForUser(realm, user1, 10);
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            int notBefore = currentSession.users().getNotBeforeOfUser(realm, user1);
            Assert.assertThat(notBefore, equalTo(10));

            // Try to update
            currentSession.users().setNotBeforeForUser(realm, user1, 20);
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            int notBefore = currentSession.users().getNotBeforeOfUser(realm, user1);
            Assert.assertThat(notBefore, equalTo(20));
            return null;
        });
    }

    private static void assertUserModel(UserModel expected, UserModel actual) {
        Assert.assertThat(actual.getUsername(), equalTo(expected.getUsername()));
        Assert.assertThat(actual.getCreatedTimestamp(), equalTo(expected.getCreatedTimestamp()));
        Assert.assertThat(actual.getFirstName(), equalTo(expected.getFirstName()));
        Assert.assertThat(actual.getLastName(), equalTo(expected.getLastName()));
        Assert.assertThat(actual.getRequiredActionsStream().collect(Collectors.toSet()),
            containsInAnyOrder(expected.getRequiredActionsStream().toArray()));
    }

    private Void addRemoveUser(int i) {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user-" + i);

            IntStream.range(0, NUM_GROUPS / 20).forEach(gIndex -> {
                user.joinGroup(session.groups().getGroupById(realm, groupIds.get((i + gIndex) % NUM_GROUPS)));
            });

            UserModel obtainedUser = session.users().getUserById(realm, user.getId());

            assertThat(obtainedUser, Matchers.notNullValue());
            assertThat(obtainedUser.getUsername(), is("user-" + i));

            Set<String> userGroupIds = obtainedUser.getGroupsStream().map(GroupModel::getName).collect(Collectors.toSet());
            assertThat(userGroupIds, hasSize(NUM_GROUPS / 20));
            assertThat(userGroupIds, hasItem("group-" + i));
            assertThat(userGroupIds, hasItem("group-" + (i - 1 + (NUM_GROUPS / 20)) % NUM_GROUPS));

            obtainedUser.leaveGroup(session.groups().getGroupById(realm, groupIds.get(i)));
            userGroupIds = obtainedUser.getGroupsStream().map(GroupModel::getName).collect(Collectors.toSet());
            assertThat(userGroupIds, hasSize((NUM_GROUPS / 20) - 1));

            assertTrue(session.users().removeUser(realm, user));
            assertFalse(session.users().removeUser(realm, user));
            assertNull(session.users().getUserByUsername(realm, user.getUsername()));

            return null;
        });
        return null;
    }

    @Test
    public void testAddRemoveUser() {
        addRemoveUser(1);
    }

    @Test
    public void testAddRemoveUserConcurrent() {
        IntStream.range(0,100).parallel().forEach(i -> addRemoveUser(i));
    }

    @SuppressWarnings("java:S5778")
    @Test
    public void testAddUserExistingMailAsUsernameWithoutConflict() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            session.users().addUser(realm, "info@bob");
            assertNotNull(session.users().getUserByUsername(realm, "info@bob"));

            return null;
        });
    }

    @SuppressWarnings("java:S5778")
    @Test
    public void testAddUserExistingMailAsUsernameThrows() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            assertThrows(ModelDuplicateException.class, () -> session.users().addUser(realm, "info@alice"));

            return null;
        });
    }

    @Test
    public void testAddUserExistingMailAsUsernameNoThrowIfCheckDisabled() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, false);
            realm.setDuplicateEmailsAllowed(false);
            session.users().addUser(realm, "info@alice");
            assertNotNull(session.users().getUserByUsername(realm, "info@alice"));

            return null;
        });
    }

    @Test
    public void testAddUserExistingMailAsUsernameNoThrowIfDuplicatesAllowed() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");

            realm.setAttribute(CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(true);
            session.users().addUser(realm, "info@alice");
            assertNotNull(session.users().getUserByUsername(realm, "info@alice"));

            return null;
        });
    }

    @Test
    public void testCaseSensitivityGetUserByUsername() {
        withRealm(realm1RealmId, (session, realm) -> {
            realm.setAttribute(Constants.REALM_ATTR_USERNAME_CASE_SENSITIVE, true);

            UserModel user1 = session.users().addUser(realm, "user");
            UserModel user2 = session.users().addUser(realm, "USER");

            return null;
        });

        // try to query storage in a separate transaction to make sure that storage can handle case-sensitive usernames
        withRealm(realm1RealmId, (session, realm) -> {
            UserModel user1 = session.users().getUserByUsername(realm, "user");
            UserModel user2 = session.users().getUserByUsername(realm, "USER");

            assertThat(user1, not(nullValue()));
            assertThat(user2, not(nullValue()));

            assertThat(user1.getUsername(), equalTo("user"));
            assertThat(user2.getUsername(), equalTo("USER"));

            return null;
        });

        withRealm(realm2RealmId, (session, realm) -> {
            realm.setAttribute(Constants.REALM_ATTR_USERNAME_CASE_SENSITIVE, false);

            UserModel user1 = session.users().addUser(realm, "user");
            assertThat(user1, not(nullValue()));

            UserProvider userProvider = session.users();
            Assert.assertThrows(ModelDuplicateException.class,
                () -> userProvider.addUser(realm, "USER"));

            return null;
        });
    }

    @Test
    public void testAddRemoveUsersInTheSameGroupConcurrent() {
        final ConcurrentSkipListSet<String> userIds = new ConcurrentSkipListSet<>();
        String groupId = groupIds.get(0);

        // Create users and let them join first group
        IntStream.range(0, 100).parallel().forEach(index -> inComittedTransaction(index, (session, i) -> {
            final RealmModel realm = session.realms().getRealm(originalRealmId);
            final UserModel user = session.users().addUser(realm, "user-" + i);
            user.joinGroup(session.groups().getGroupById(realm, groupId));
            userIds.add(user.getId());
            return null;
        }));

        withRealm(originalRealmId, (session, realm) -> {
            final GroupModel group = session.groups().getGroupById(realm, groupId);
            assertThat(session.users().getGroupMembersStream(realm, group).count(), is(100L));
            assertThat(session.users().getGroupMembersStream(realm, group, null, null).count(), is(100L));
            assertThat(session.users().getGroupMembersStream(realm, group, 10, -1).count(), is(90L));
            assertThat(session.users().getGroupMembersStream(realm, group, -1, 90).count(), is(90L));
            assertThat(session.users().getGroupMembersStream(realm, group, 10, 150).count(), is(90L));
            return null;
        });

        userIds.stream().parallel().forEach(index -> inComittedTransaction(index, (session, userId) -> {
            final RealmModel realm = session.realms().getRealm(originalRealmId);
            final UserModel user = session.users().getUserById(realm, userId);
            log.debugf("Remove user %s: %s", userId, session.users().removeUser(realm, user));
            return null;
        }));

        withRealm(originalRealmId, (session, realm) -> {
            final GroupModel group = session.groups().getGroupById(realm, groupId);
            assertThat(session.users().getGroupMembersStream(realm, group).collect(Collectors.toList()), Matchers.empty());
            return null;
        });
    }
}

