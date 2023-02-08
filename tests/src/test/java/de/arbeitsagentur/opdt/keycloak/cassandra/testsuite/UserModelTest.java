package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.*;
import org.keycloak.models.map.realm.MapRealmProviderFactory;
import org.keycloak.models.map.user.MapUserProviderFactory;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.managers.ClientManager;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.storage.user.UserRegistrationProvider;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

/**
 *
 * Ported from:
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */

@RequireProvider(UserProvider.class)
@RequireProvider(RealmProvider.class)
public class UserModelTest extends KeycloakModelTest {

    private String originalRealmId;
    private String otherRealmId;
    private String realm1RealmId;
    private String realm2RealmId;
    @Override
    public void createEnvironment(KeycloakSession s) {
        originalRealmId = s.realms().createRealm("original").getId();
        otherRealmId = s.realms().createRealm("other").getId();
        realm1RealmId = s.realms().createRealm("realm1").getId();
        realm2RealmId = s.realms().createRealm("realm2").getId();
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        s.realms().removeRealm(originalRealmId);
        s.realms().removeRealm(otherRealmId);
        s.realms().removeRealm(realm1RealmId);
        s.realms().removeRealm(realm2RealmId);
    }

    @Test
    public void persistUser() {
        withRealm(originalRealmId, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setFirstName("first-name");
            user.setLastName("last-name");
            user.setEmail("email");
            assertNotNull(user.getCreatedTimestamp());
            // test that timestamp is current with 10s tollerance
            Assert.assertTrue((System.currentTimeMillis() - user.getCreatedTimestamp()) < 10000);

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
    public void webOriginSetTest() {
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
            Assert.assertThat(allAttrVals.keySet(), hasSize(6));
            Assert.assertThat(allAttrVals.keySet(), containsInAnyOrder(UserModel.USERNAME, UserModel.FIRST_NAME, UserModel.LAST_NAME, UserModel.EMAIL, "key1", "key2"));
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
            return null;
        });

        withRealm(originalRealmId, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "user");

            // Update attribute
            List<String> attrVals = new ArrayList<>(Arrays.asList("val2"));
            user.setAttribute("key1", attrVals);
            Map<String, List<String>> allAttrVals = user.getAttributes();

            // Ensure same transaction is able to see updated value
            Assert.assertThat(allAttrVals.keySet(), hasSize(5));
            Assert.assertThat(allAttrVals.keySet(), containsInAnyOrder("key1", UserModel.FIRST_NAME, UserModel.LAST_NAME, UserModel.EMAIL, UserModel.USERNAME));
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
            Assert.assertTrue(user1.hasRole(role1));
            Assert.assertTrue(user2.hasRole(role1));

            RealmModel realm2 = currentSession.realms().getRealmByName("realm2");
            UserModel realm2User1 = currentSession.users().getUserByUsername(realm2, "user1");
            Assert.assertFalse(realm2User1.hasRole(role1));

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
}
