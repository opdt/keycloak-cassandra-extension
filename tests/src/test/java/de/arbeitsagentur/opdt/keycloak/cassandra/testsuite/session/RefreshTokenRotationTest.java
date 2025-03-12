package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session;

import static org.junit.Assert.*;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.KeycloakModelTest;
import org.junit.Test;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;

public class RefreshTokenRotationTest extends KeycloakModelTest {

    private String realmId;

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = createRealm(s, "test");
        realm.setDefaultRole(
                s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));

        s.users().addUser(realm, "user1").setEmail("user1@localhost");
        s.clients().addClient(realm, "testClient");

        this.realmId = realm.getId();
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().getRealm(realmId);
        s.sessions().removeUserSessions(realm);

        UserModel user1 = s.users().getUserByUsername(realm, "user1");

        UserManager um = new UserManager(s);
        if (user1 != null) {
            um.removeUser(realm, user1);
        }

        ClientModel testClient = s.clients().getClientByClientId(realm, "testClient");
        s.clients().removeClient(realm, testClient.getId());

        s.realms().removeRealm(realmId);
    }

    @Test
    public void testRefreshTokenRotationMultiTab() {
        withRealm(realmId, (session, realm) -> {
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            realm.setClientSessionIdleTimeout(1000);

            realm.setAttribute("refreshTokenReuseInterval", 100);
            realm.setRefreshTokenMaxReuse(0);
            return null;
        });

        String uSId = withRealm(realmId, (session, realm) -> {
            UserSessionModel userSession = session.sessions()
                    .createUserSession(
                            realm,
                            session.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.1",
                            "form",
                            true,
                            null,
                            null);

            ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
            session.sessions().createClientSession(realm, testClient, userSession);

            return userSession.getId();
        });

        // Refresh Tab 1
        withRealm(realmId, (session, realm) -> {
            UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);

            ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
            AuthenticatedClientSessionModel clientSession =
                    userSession.getAuthenticatedClientSessionByClient(testClient.getId());

            assertEquals(0, clientSession.getRefreshTokenUseCount("id1"));

            clientSession.setRefreshTokenUseCount("id1", 1);

            return null;
        });

        // Reuse inside given interval
        withRealm(realmId, (session, realm) -> {
            UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);

            ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
            AuthenticatedClientSessionModel clientSession =
                    userSession.getAuthenticatedClientSessionByClient(testClient.getId());

            assertEquals(0, clientSession.getRefreshTokenUseCount("id1"));

            clientSession.setRefreshTokenUseCount("id1", 1);

            return null;
        });

        // Reuse outside given interval
        Time.setOffset(101);
        withRealm(realmId, (session, realm) -> {
            UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);

            ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
            AuthenticatedClientSessionModel clientSession =
                    userSession.getAuthenticatedClientSessionByClient(testClient.getId());

            assertEquals(1, clientSession.getRefreshTokenUseCount("id1"));

            return null;
        });

        // Refresh Tab 2
        withRealm(realmId, (session, realm) -> {
            UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);

            ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
            AuthenticatedClientSessionModel clientSession =
                    userSession.getAuthenticatedClientSessionByClient(testClient.getId());

            assertEquals(0, clientSession.getRefreshTokenUseCount("id2"));

            clientSession.setRefreshTokenUseCount("id2", 1);

            return null;
        });
    }
}
