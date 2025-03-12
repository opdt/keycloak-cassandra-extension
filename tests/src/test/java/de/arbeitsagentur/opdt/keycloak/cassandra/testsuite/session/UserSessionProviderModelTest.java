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

package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session;

import static de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session.SessionTestUtils.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.KeycloakModelTest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.keycloak.common.Profile;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;

public class UserSessionProviderModelTest extends KeycloakModelTest {
    private String realmId;

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = createRealm(s, "test");
        realm.setOfflineSessionIdleTimeout(Constants.DEFAULT_OFFLINE_SESSION_IDLE_TIMEOUT);
        realm.setDefaultRole(
                s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
        realm.setSsoSessionIdleTimeout(1800);
        realm.setSsoSessionMaxLifespan(36000);
        realm.setClientSessionIdleTimeout(500);
        this.realmId = realm.getId();

        s.users().addUser(realm, "user1").setEmail("user1@localhost");
        s.users().addUser(realm, "user2").setEmail("user2@localhost");

        createClients(s, realm);
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().getRealm(realmId);
        s.sessions().removeUserSessions(realm);

        UserModel user1 = s.users().getUserByUsername(realm, "user1");
        UserModel user2 = s.users().getUserByUsername(realm, "user2");

        UserManager um = new UserManager(s);
        if (user1 != null) {
            um.removeUser(realm, user1);
        }
        if (user2 != null) {
            um.removeUser(realm, user2);
        }

        s.realms().removeRealm(realmId);
    }

    // Copied / Adapted from org.keycloak.testsuite.model.session.UserSessionProviderModelTest

    @Test
    public void testMultipleSessionsRemovalInOneTransaction() {
        UserSessionModel[] origSessions = inComittedTransaction(session -> {
            return createSessions(session, realmId);
        });

        inComittedTransaction(session -> {
            RealmModel realm = session.realms().getRealm(realmId);

            UserSessionModel userSession = session.sessions().getUserSession(realm, origSessions[0].getId());
            Assert.assertEquals(origSessions[0], userSession);

            userSession = session.sessions().getUserSession(realm, origSessions[1].getId());
            Assert.assertEquals(origSessions[1], userSession);
        });

        inComittedTransaction(session -> {
            RealmModel realm = session.realms().getRealm(realmId);

            session.sessions().removeUserSession(realm, origSessions[0]);
            session.sessions().removeUserSession(realm, origSessions[1]);
        });

        inComittedTransaction(session -> {
            RealmModel realm = session.realms().getRealm(realmId);

            UserSessionModel userSession = session.sessions().getUserSession(realm, origSessions[0].getId());
            Assert.assertNull(userSession);

            userSession = session.sessions().getUserSession(realm, origSessions[1].getId());
            Assert.assertNull(userSession);
        });
    }

    @Test
    public void testExpiredClientSessions() {
        UserSessionModel[] origSessions = inComittedTransaction(session -> {
            // create some user and client sessions
            return createSessions(session, realmId);
        });

        AtomicReference<List<String>> clientSessionIds = new AtomicReference<>();
        clientSessionIds.set(origSessions[0].getAuthenticatedClientSessions().values().stream()
                .map(AuthenticatedClientSessionModel::getId)
                .collect(Collectors.toList()));

        inComittedTransaction(session -> {
            RealmModel realm = session.realms().getRealm(realmId);

            UserSessionModel userSession = session.sessions().getUserSession(realm, origSessions[0].getId());
            Assert.assertEquals(origSessions[0], userSession);

            AuthenticatedClientSessionModel clientSession = session.sessions()
                    .getClientSession(
                            userSession,
                            realm.getClientByClientId("test-app"),
                            origSessions[0]
                                    .getAuthenticatedClientSessionByClient(realm.getClientByClientId("test-app")
                                            .getId())
                                    .getId(),
                            false);
            Assert.assertEquals(
                    origSessions[0]
                            .getAuthenticatedClientSessionByClient(
                                    realm.getClientByClientId("test-app").getId())
                            .getId(),
                    clientSession.getId());

            userSession = session.sessions().getUserSession(realm, origSessions[1].getId());
            Assert.assertEquals(origSessions[1], userSession);
        });

        // not possible to expire client session without expiring user sessions with time offset in map
        // storage because
        // expiration in map storage takes min of (clientSessionIdleExpiration, ssoSessionIdleTimeout)
        inComittedTransaction(session -> {
            RealmModel realm = session.realms().getRealm(realmId);

            UserSessionModel userSession = session.sessions().getUserSession(realm, origSessions[0].getId());

            userSession.getAuthenticatedClientSessions().values().stream().forEach(clientSession -> {
                // expire client sessions
                clientSession.setTimestamp(1);
            });
        });

        inComittedTransaction(session -> {
            RealmModel realm = session.realms().getRealm(realmId);

            // assert the user session is still there
            UserSessionModel userSession = session.sessions().getUserSession(realm, origSessions[0].getId());
            Assert.assertEquals(origSessions[0], userSession);

            // assert the client sessions are expired
            clientSessionIds.get().forEach(clientSessionId -> {
                Assert.assertNull(session.sessions()
                        .getClientSession(userSession, realm.getClientByClientId("test-app"), clientSessionId, false));
                Assert.assertNull(session.sessions()
                        .getClientSession(
                                userSession, realm.getClientByClientId("third-party"), clientSessionId, false));
            });
        });
    }

    @Test
    public void testTransientUserSessionIsNotPersisted() {
        String id = inComittedTransaction(session -> {
            RealmModel realm = session.realms().getRealm(realmId);
            UserSessionModel userSession = session.sessions()
                    .createUserSession(
                            KeycloakModelUtils.generateId(),
                            realm,
                            session.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.1",
                            "form",
                            false,
                            null,
                            null,
                            UserSessionModel.SessionPersistenceState.TRANSIENT);

            ClientModel testApp = realm.getClientByClientId("test-app");
            AuthenticatedClientSessionModel clientSession =
                    session.sessions().createClientSession(realm, testApp, userSession);

            // assert the client sessions are present
            assertThat(
                    session.sessions().getClientSession(userSession, testApp, clientSession.getId(), false),
                    notNullValue());
            return userSession.getId();
        });

        inComittedTransaction(session -> {
            RealmModel realm = session.realms().getRealm(realmId);
            UserSessionModel userSession = session.sessions().getUserSession(realm, id);

            // in new transaction transient session should not be present
            assertThat(userSession, nullValue());
        });
    }

    @Test
    public void testClientSessionIsNotPersistedForTransientUserSession() {
        Object[] transientUserSessionWithClientSessionId = inComittedTransaction(session -> {
            RealmModel realm = session.realms().getRealm(realmId);
            UserSessionModel userSession = session.sessions()
                    .createUserSession(
                            null,
                            realm,
                            session.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.1",
                            "form",
                            false,
                            null,
                            null,
                            UserSessionModel.SessionPersistenceState.TRANSIENT);
            ClientModel testApp = realm.getClientByClientId("test-app");
            AuthenticatedClientSessionModel clientSession =
                    session.sessions().createClientSession(realm, testApp, userSession);

            // assert the client sessions are present
            assertThat(
                    session.sessions().getClientSession(userSession, testApp, clientSession.getId(), false),
                    notNullValue());
            Object[] result = new Object[2];
            result[0] = userSession;
            result[1] = clientSession.getId();
            return result;
        });
        inComittedTransaction(session -> {
            RealmModel realm = session.realms().getRealm(realmId);
            ClientModel testApp = realm.getClientByClientId("test-app");
            UserSessionModel userSession = (UserSessionModel) transientUserSessionWithClientSessionId[0];
            String clientSessionId = (String) transientUserSessionWithClientSessionId[1];
            // in new transaction transient session should not be present
            assertThat(session.sessions().getClientSession(userSession, testApp, clientSessionId, false), nullValue());
        });
    }

    @Test
    @Ignore("Flaky")
    public void testCreateUserSessionsParallel() throws InterruptedException {
        Set<String> userSessionIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch latch = new CountDownLatch(4);

        inIndependentFactories(4, 30, () -> {
            withRealm(realmId, (session, realm) -> {
                UserModel user = session.users().getUserByUsername(realm, "user1");
                UserSessionModel userSession =
                        session.sessions().createUserSession(realm, user, "user1", "", "", false, null, null);
                userSessionIds.add(userSession.getId());

                latch.countDown();

                return null;
            });

            // wait for other nodes to finish
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            assertThat(userSessionIds, Matchers.iterableWithSize(4));

            // wait a bit to allow replication
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            withRealm(realmId, (session, realm) -> {
                userSessionIds.forEach(
                        id -> Assert.assertNotNull(session.sessions().getUserSession(realm, id)));

                return null;
            });
        });
    }

    // Based off of UserSessionProviderTests (Arquillian)
    @Test
    public void testCreateSessions() {
        int started = Time.currentTime();

        withRealm(realmId, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            assertSession(
                    s.sessions().getUserSession(r, sessions[0].getId()),
                    s.users().getUserByUsername(r, "user1"),
                    "127.0.0.1",
                    started,
                    started,
                    "test-app",
                    "third-party");
            assertSession(
                    s.sessions().getUserSession(r, sessions[1].getId()),
                    s.users().getUserByUsername(r, "user1"),
                    "127.0.0.2",
                    started,
                    started,
                    "test-app");
            assertSession(
                    s.sessions().getUserSession(r, sessions[2].getId()),
                    s.users().getUserByUsername(r, "user2"),
                    "127.0.0.3",
                    started,
                    started,
                    "test-app");
            return null;
        });
    }

    @Test
    public void testCreateSessionsTransientUser() {
        Profile.init(
                Profile.ProfileName.DEFAULT,
                Map.of(
                        Profile.Feature.TRANSIENT_USERS,
                        true,
                        Profile.Feature.AUTHORIZATION,
                        false,
                        Profile.Feature.ADMIN_FINE_GRAINED_AUTHZ,
                        false));
        int started = Time.currentTime();

        withRealm(realmId, (s, r) -> {
            UserSessionModel[] sessions = createSessionsTransientUser(s, r.getId());
            assertSessionLightweightUser(
                    s.sessions().getUserSession(r, sessions[0].getId()),
                    "user1",
                    "127.0.0.1",
                    started,
                    started,
                    "test-app",
                    "third-party");
            assertSessionLightweightUser(
                    s.sessions().getUserSession(r, sessions[1].getId()),
                    "user1",
                    "127.0.0.2",
                    started,
                    started,
                    "test-app");
            assertSessionLightweightUser(
                    s.sessions().getUserSession(r, sessions[2].getId()),
                    "user2",
                    "127.0.0.3",
                    started,
                    started,
                    "test-app");
            return null;
        });
    }

    @Test
    public void testUpdateSession() {
        int lastRefresh = Time.currentTime();
        withRealm(realmId, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            s.sessions().getUserSession(r, sessions[0].getId()).setLastSessionRefresh(lastRefresh);
            assertEquals(
                    lastRefresh,
                    s.sessions().getUserSession(r, sessions[0].getId()).getLastSessionRefresh());
            return null;
        });
    }

    @Test
    public void testUpdateSessionInSameTransaction() {
        int lastRefresh = Time.currentTime();
        withRealm(realmId, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            s.sessions().getUserSession(r, sessions[0].getId()).setLastSessionRefresh(lastRefresh);
            assertEquals(
                    lastRefresh,
                    s.sessions().getUserSession(r, sessions[0].getId()).getLastSessionRefresh());
            return null;
        });
    }

    @Test
    public void testRestartSession() {
        int started = Time.currentTime();

        Time.setOffset(100);
        try {
            withRealm(realmId, (s, r) -> {
                UserSessionModel[] sessions = createSessions(s, r.getId());
                UserSessionModel userSession = s.sessions().getUserSession(r, sessions[0].getId());
                assertSession(
                        userSession,
                        s.users().getUserByUsername(r, "user1"),
                        "127.0.0.1",
                        started,
                        started,
                        "test-app",
                        "third-party");

                userSession.restartSession(
                        r, s.users().getUserByUsername(r, "user2"), "user2", "127.0.0.6", "form", true, null, null);

                userSession = s.sessions().getUserSession(r, sessions[0].getId());
                assertSession(
                        userSession,
                        s.users().getUserByUsername(r, "user2"),
                        "127.0.0.6",
                        started + 100,
                        started + 100);
                return null;
            });
        } finally {
            Time.setOffset(0);
        }
    }

    @Test
    public void testCreateClientSession() {
        withRealm(realmId, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            Map<String, AuthenticatedClientSessionModel> clientSessions =
                    s.sessions().getUserSession(r, sessions[0].getId()).getAuthenticatedClientSessions();
            assertEquals(2, clientSessions.size());

            String clientUUID = r.getClientByClientId("test-app").getId();

            AuthenticatedClientSessionModel session1 = clientSessions.get(clientUUID);

            assertNull(session1.getAction());
            assertEquals(
                    r.getClientByClientId("test-app").getClientId(),
                    session1.getClient().getClientId());
            assertEquals(sessions[0].getId(), session1.getUserSession().getId());
            assertEquals("http://redirect", session1.getRedirectUri());
            assertEquals("state", session1.getNote(OIDCLoginProtocol.STATE_PARAM));
            return null;
        });
    }

    @Test
    public void testUpdateClientSession() {
        withRealm(realmId, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            String userSessionId = sessions[0].getId();
            String clientUUID = r.getClientByClientId("test-app").getId();
            UserSessionModel userSession = s.sessions().getUserSession(r, userSessionId);
            AuthenticatedClientSessionModel clientSession =
                    userSession.getAuthenticatedClientSessions().get(clientUUID);

            int time = clientSession.getTimestamp();
            assertNull(clientSession.getAction());

            clientSession.setAction(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name());
            clientSession.setTimestamp(time + 10);

            AuthenticatedClientSessionModel updated = s.sessions()
                    .getUserSession(r, userSessionId)
                    .getAuthenticatedClientSessions()
                    .get(clientUUID);
            assertEquals(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name(), updated.getAction());
            assertEquals(time + 10, updated.getTimestamp());
            return null;
        });
    }

    @Test
    public void testUpdateClientSessionWithGetByClientId() {
        withRealm(realmId, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            String userSessionId = sessions[0].getId();
            String clientUUID = r.getClientByClientId("test-app").getId();
            UserSessionModel userSession = s.sessions().getUserSession(r, userSessionId);
            AuthenticatedClientSessionModel clientSession =
                    userSession.getAuthenticatedClientSessionByClient(clientUUID);

            int time = clientSession.getTimestamp();
            assertNull(clientSession.getAction());

            clientSession.setAction(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name());
            clientSession.setTimestamp(time + 10);

            AuthenticatedClientSessionModel updated =
                    s.sessions().getUserSession(r, userSessionId).getAuthenticatedClientSessionByClient(clientUUID);
            assertEquals(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name(), updated.getAction());
            assertEquals(time + 10, updated.getTimestamp());
            return null;
        });
    }

    @Test
    public void testUpdateClientSessionInSameTransaction() {
        withRealm(realmId, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            String userSessionId = sessions[0].getId();
            String clientUUID = r.getClientByClientId("test-app").getId();
            UserSessionModel userSession = s.sessions().getUserSession(r, userSessionId);
            AuthenticatedClientSessionModel clientSession =
                    userSession.getAuthenticatedClientSessionByClient(clientUUID);

            clientSession.setAction(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name());
            clientSession.setNote("foo", "bar");

            AuthenticatedClientSessionModel updated =
                    s.sessions().getUserSession(r, userSessionId).getAuthenticatedClientSessionByClient(clientUUID);
            assertEquals(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name(), updated.getAction());
            assertEquals("bar", updated.getNote("foo"));
            return null;
        });
    }

    @Test
    public void testGetUserSessions() {
        withRealm(realmId, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            assertSessions(
                    s.sessions()
                            .getUserSessionsStream(r, s.users().getUserByUsername(r, "user1"))
                            .collect(Collectors.toList()),
                    sessions[0],
                    sessions[1]);
            assertSessions(
                    s.sessions()
                            .getUserSessionsStream(r, s.users().getUserByUsername(r, "user2"))
                            .collect(Collectors.toList()),
                    sessions[2]);
            return null;
        });
    }

    @Test
    public void testRemoveUserSessionsByUser() {
        withRealm(realmId, (s, r) -> createSessions(s, r.getId()));

        final Map<String, Integer> clientSessionsKept = new HashMap<>();
        withRealm(realmId, (s, r) -> {
            clientSessionsKept.putAll(s.sessions()
                    .getUserSessionsStream(r, s.users().getUserByUsername(r, "user2"))
                    .collect(Collectors.toMap(UserSessionModel::getId, model -> model.getAuthenticatedClientSessions()
                            .keySet()
                            .size())));

            s.sessions().removeUserSessions(r, s.users().getUserByUsername(r, "user1"));
            return null;
        });

        withRealm(realmId, (s, r) -> {
            assertEquals(
                    0,
                    s.sessions()
                            .getUserSessionsStream(r, s.users().getUserByUsername(r, "user1"))
                            .count());
            List<UserSessionModel> userSessions = s.sessions()
                    .getUserSessionsStream(r, s.users().getUserByUsername(r, "user2"))
                    .collect(Collectors.toList());

            assertSame(userSessions.size(), 1);

            for (UserSessionModel userSession : userSessions) {
                Assert.assertEquals(
                        (int) clientSessionsKept.get(userSession.getId()),
                        userSession.getAuthenticatedClientSessions().size());
            }
            return null;
        });
    }

    @Test
    public void testRemoveUserSession() {
        withRealm(realmId, (s, r) -> {
            UserSessionModel userSession = createSessions(s, r.getId())[0];

            s.sessions().removeUserSession(r, userSession);

            assertNull(s.sessions().getUserSession(r, userSession.getId()));
            return null;
        });
    }

    @Test
    public void testRemoveUserSessionsByRealm() {
        withRealm(realmId, (s, r) -> {
            createSessions(s, r.getId());
            s.sessions().removeUserSessions(r);

            assertEquals(
                    0,
                    s.sessions()
                            .getUserSessionsStream(r, s.users().getUserByUsername(r, "user1"))
                            .count());
            assertEquals(
                    0,
                    s.sessions()
                            .getUserSessionsStream(r, s.users().getUserByUsername(r, "user2"))
                            .count());
            return null;
        });
    }

    @Test
    public void testOnClientRemoved() {
        withRealm(realmId, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());

            String thirdPartyClientUUID = r.getClientByClientId("third-party").getId();

            Map<String, Set<String>> clientSessionsKept = new HashMap<>();
            for (UserSessionModel session : sessions) {
                // session associated with the model was closed, load it by id into a new session
                session = s.sessions().getUserSession(r, session.getId());
                Set<String> clientUUIDS =
                        new HashSet<>(session.getAuthenticatedClientSessions().keySet());
                clientUUIDS.remove(thirdPartyClientUUID); // This client will be later removed, hence his
                // clientSessions too
                clientSessionsKept.put(session.getId(), clientUUIDS);
            }

            r.removeClient(thirdPartyClientUUID);

            for (UserSessionModel session : sessions) {
                session = s.sessions().getUserSession(r, session.getId());
                Set<String> clientUUIDS =
                        session.getAuthenticatedClientSessions().keySet();
                assertEquals(clientUUIDS, clientSessionsKept.get(session.getId()));
            }

            // Revert client
            r.addClient("third-party");
            return null;
        });
    }

    @Test
    public void testTransientUserSession() {
        String userSessionId = UUID.randomUUID().toString();
        // create an user session, but don't persist it to infinispan
        withRealm(realmId, (s, r) -> {
            ClientModel client = r.getClientByClientId("test-app");
            long sessionsBefore = s.sessions().getActiveUserSessions(r, client);

            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            userSessionId,
                            r,
                            s.users().getUserByUsername(r, "user1"),
                            "user1",
                            "127.0.0.1",
                            "form",
                            true,
                            null,
                            null,
                            UserSessionModel.SessionPersistenceState.TRANSIENT);
            AuthenticatedClientSessionModel clientSession = s.sessions().createClientSession(r, client, userSession);
            assertEquals(userSession, clientSession.getUserSession());

            assertSession(
                    userSession,
                    s.users().getUserByUsername(r, "user1"),
                    "127.0.0.1",
                    userSession.getStarted(),
                    userSession.getStarted(),
                    "test-app");

            // Can find session by ID in current transaction
            UserSessionModel foundSession = s.sessions().getUserSession(r, userSessionId);
            Assert.assertEquals(userSession, foundSession);

            // Count of sessions should be still the same
            Assert.assertEquals(sessionsBefore, s.sessions().getActiveUserSessions(r, client));
            return null;
        });

        // create an user session whose last refresh exceeds the max session idle timeout.
        withRealm(realmId, (s, r) -> {
            UserSessionModel userSession = s.sessions().getUserSession(r, userSessionId);
            Assert.assertNull(userSession);
            return null;
        });
    }

    @Test
    public void testGetByClient() {
        withRealm(realmId, (s, r) -> {
            final UserSessionModel[] sessions = createSessions(s, realmId);

            KeycloakModelUtils.runJobInTransaction(s.getKeycloakSessionFactory(), (KeycloakSession kcSession) -> {
                assertSessions(
                        kcSession
                                .sessions()
                                .getUserSessionsStream(r, r.getClientByClientId("test-app"))
                                .collect(Collectors.toList()),
                        sessions[0],
                        sessions[1],
                        sessions[2]);
                assertSessions(
                        kcSession
                                .sessions()
                                .getUserSessionsStream(r, r.getClientByClientId("third-party"))
                                .collect(Collectors.toList()),
                        sessions[0]);
            });
            return null;
        });
    }

    @Test
    public void testGetByClientPaginated() {
        withRealm(realmId, (s, r) -> {
            RealmModel realm = s.realms().getRealmByName("test");

            try {
                for (int i = 0; i < 25; i++) {
                    Time.setOffset(i);
                    UserSessionModel userSession = s.sessions()
                            .createUserSession(
                                    realm,
                                    s.users().getUserByUsername(realm, "user1"),
                                    "user1",
                                    "127.0.0." + i,
                                    "form",
                                    false,
                                    null,
                                    null);
                    AuthenticatedClientSessionModel clientSession =
                            s.sessions().createClientSession(realm, realm.getClientByClientId("test-app"), userSession);
                    assertNotNull(clientSession);
                    clientSession.setRedirectUri("http://redirect");
                    clientSession.setNote(OIDCLoginProtocol.STATE_PARAM, "state");
                    clientSession.setTimestamp(userSession.getStarted());
                    userSession.setLastSessionRefresh(userSession.getStarted());
                }
            } finally {
                Time.setOffset(0);
            }
            return null;
        });

        withRealm(realmId, (s, r) -> {
            assertPaginatedSession(s, r, r.getClientByClientId("test-app"), 0, 1, 1);
            assertPaginatedSession(s, r, r.getClientByClientId("test-app"), 0, 10, 10);
            assertPaginatedSession(s, r, r.getClientByClientId("test-app"), 10, 10, 10);
            assertPaginatedSession(s, r, r.getClientByClientId("test-app"), 20, 10, 5);
            assertPaginatedSession(s, r, r.getClientByClientId("test-app"), 30, 10, 0);
            return null;
        });
    }

    @Test
    public void testCreateAndGetInSameTransaction() {
        withRealm(realmId, (s, r) -> {
            RealmModel realm = s.realms().getRealmByName("test");
            ClientModel client = realm.getClientByClientId("test-app");
            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);
            AuthenticatedClientSessionModel clientSession =
                    createClientSession(s, realmId, client, userSession, "http://redirect", "state");

            UserSessionModel userSessionLoaded = s.sessions().getUserSession(realm, userSession.getId());
            AuthenticatedClientSessionModel clientSessionLoaded =
                    userSessionLoaded.getAuthenticatedClientSessions().get(client.getId());
            Assert.assertNotNull(userSessionLoaded);
            Assert.assertNotNull(clientSessionLoaded);

            Assert.assertEquals(
                    userSession.getId(), clientSessionLoaded.getUserSession().getId());
            Assert.assertEquals(
                    1, userSessionLoaded.getAuthenticatedClientSessions().size());
            return null;
        });
    }

    @Test
    public void testAuthenticatedClientSessions() {
        withRealm(realmId, (s, r) -> {
            RealmModel realm = s.realms().getRealmByName("test");
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);

            ClientModel client1 = realm.getClientByClientId("test-app");
            ClientModel client2 = realm.getClientByClientId("third-party");

            // Create client1 session
            AuthenticatedClientSessionModel clientSession1 =
                    s.sessions().createClientSession(realm, client1, userSession);
            clientSession1.setAction("foo1");
            int currentTime1 = Time.currentTime();
            clientSession1.setTimestamp(currentTime1);

            // Create client2 session
            AuthenticatedClientSessionModel clientSession2 =
                    s.sessions().createClientSession(realm, client2, userSession);
            clientSession2.setAction("foo2");
            int currentTime2 = Time.currentTime();
            clientSession2.setTimestamp(currentTime2);

            // Ensure sessions are here
            userSession = s.sessions().getUserSession(realm, userSession.getId());
            Map<String, AuthenticatedClientSessionModel> clientSessions = userSession.getAuthenticatedClientSessions();
            Assert.assertEquals(2, clientSessions.size());
            testAuthenticatedClientSession(
                    clientSessions.get(client1.getId()), "test-app", userSession.getId(), "foo1", currentTime1);
            testAuthenticatedClientSession(
                    clientSessions.get(client2.getId()), "third-party", userSession.getId(), "foo2", currentTime2);

            // Update session1
            clientSessions.get(client1.getId()).setAction("foo1-updated");

            // Ensure updated
            userSession = s.sessions().getUserSession(realm, userSession.getId());
            clientSessions = userSession.getAuthenticatedClientSessions();
            testAuthenticatedClientSession(
                    clientSessions.get(client1.getId()), "test-app", userSession.getId(), "foo1-updated", currentTime1);

            // Rewrite session2
            clientSession2 = s.sessions().createClientSession(realm, client2, userSession);
            clientSession2.setAction("foo2-rewrited");
            int currentTime3 = Time.currentTime();
            clientSession2.setTimestamp(currentTime3);

            // Ensure updated
            userSession = s.sessions().getUserSession(realm, userSession.getId());
            clientSessions = userSession.getAuthenticatedClientSessions();
            Assert.assertEquals(2, clientSessions.size());
            testAuthenticatedClientSession(
                    clientSessions.get(client1.getId()), "test-app", userSession.getId(), "foo1-updated", currentTime1);
            testAuthenticatedClientSession(
                    clientSessions.get(client2.getId()),
                    "third-party",
                    userSession.getId(),
                    "foo2-rewrited",
                    currentTime3);

            // remove session
            clientSession1 = userSession.getAuthenticatedClientSessions().get(client1.getId());
            clientSession1.detachFromUserSession();

            userSession = s.sessions().getUserSession(realm, userSession.getId());
            clientSessions = userSession.getAuthenticatedClientSessions();
            Assert.assertEquals(1, clientSessions.size());
            Assert.assertNull(clientSessions.get(client1.getId()));
            return null;
        });
    }

    private static void testAuthenticatedClientSession(
            AuthenticatedClientSessionModel clientSession,
            String expectedClientId,
            String expectedUserSessionId,
            String expectedAction,
            int expectedTimestamp) {
        Assert.assertEquals(expectedClientId, clientSession.getClient().getClientId());
        Assert.assertEquals(
                expectedUserSessionId, clientSession.getUserSession().getId());
        Assert.assertEquals(expectedAction, clientSession.getAction());
        Assert.assertEquals(expectedTimestamp, clientSession.getTimestamp());
    }

    private static void assertPaginatedSession(
            KeycloakSession session, RealmModel realm, ClientModel client, int start, int max, int expectedSize) {
        assertEquals(
                expectedSize,
                session.sessions()
                        .getUserSessionsStream(realm, client, start, max)
                        .count());
    }

    // Own Tests
    @Test
    public void testUserSessionNotes() {
        String sessionId = withRealm(realmId, (s, realm) -> {
            UserModel testuser = s.users().getUserByUsername(realm, "user1");
            UserSessionModel session =
                    s.sessions().createUserSession(realm, testuser, "testuser", "127.0.0.1", "test", false, null, null);
            session.setNote("key1", "value1");
            session.setNote("key2", "value2");

            UserSessionModel newlyLoadedSession = s.sessions().getUserSession(realm, session.getId());
            newlyLoadedSession.setNote("key3", "value3");

            UserSessionModel currentSession = s.sessions().getUserSession(realm, session.getId());
            assertThat(currentSession.getNotes().entrySet(), hasSize(4));
            assertTrue(currentSession.getNotes().containsKey("KC_DEVICE_NOTE"));
            assertThat(currentSession.getNotes().get("key1"), equalTo("value1"));
            assertThat(currentSession.getNotes().get("key2"), equalTo("value2"));
            assertThat(currentSession.getNotes().get("key3"), equalTo("value3"));

            return session.getId();
        });

        // New transaction
        withRealm(realmId, (s, realm) -> {
            UserModel testuser = s.users().getUserByUsername(realm, "user1");
            UserSessionModel session = s.sessions().getUserSession(realm, sessionId);
            session.setNote("key4", "value4");

            UserSessionModel currentSession = s.sessions().getUserSession(realm, sessionId);
            assertThat(currentSession.getNotes().entrySet(), hasSize(5));
            assertTrue(currentSession.getNotes().containsKey("KC_DEVICE_NOTE"));
            assertThat(currentSession.getNotes().get("key1"), equalTo("value1"));
            assertThat(currentSession.getNotes().get("key2"), equalTo("value2"));
            assertThat(currentSession.getNotes().get("key3"), equalTo("value3"));
            assertThat(currentSession.getNotes().get("key4"), equalTo("value4"));

            return null;
        });
    }

    @Test
    public void testClientSessionToUserSessionReference() {
        withRealm(realmId, (s, realm) -> {
            UserModel testuser = s.users().getUserByUsername(realm, "user1");
            ClientModel client = s.clients().addClient(realm, "testclient");
            UserSessionModel session =
                    s.sessions().createUserSession(realm, testuser, "testuser", "127.0.0.1", "test", false, null, null);
            session.setNote("key1", "value1");

            AuthenticatedClientSessionModel clientSession = s.sessions().createClientSession(realm, client, session);
            clientSession.setNote("ckey", "cval");

            session.setNote("key2", "value2");
            clientSession.getUserSession().setNote("key3", "value3");

            UserSessionModel currentSession = s.sessions().getUserSession(realm, session.getId());
            assertThat(currentSession.getNotes().entrySet(), hasSize(4));
            assertTrue(currentSession.getNotes().containsKey("KC_DEVICE_NOTE"));
            assertThat(currentSession.getNotes().get("key1"), equalTo("value1"));
            assertThat(currentSession.getNotes().get("key2"), equalTo("value2"));
            assertThat(currentSession.getNotes().get("key3"), equalTo("value3"));

            return session.getId();
        });
    }

    @Test
    public void testBrokerUserSessions() {
        withRealm(realmId, (s, realm) -> {
            UserModel testuser = s.users().getUserByUsername(realm, "user1");
            UserSessionModel session = s.sessions()
                    .createUserSession(
                            realm, testuser, "testuser", "127.0.0.1", "test", false, "brokerSession", "brokerUserId");

            UserSessionModel currentSession = s.sessions().getUserSessionByBrokerSessionId(realm, "brokerSession");
            assertThat(currentSession.getBrokerSessionId(), is("brokerSession"));
            assertThat(currentSession.getBrokerUserId(), is("brokerUserId"));

            List<UserSessionModel> brokerSessions = s.sessions()
                    .getUserSessionByBrokerUserIdStream(realm, "brokerUserId")
                    .collect(Collectors.toList());
            assertThat(brokerSessions, hasSize(1));
            assertThat(brokerSessions.get(0).getBrokerSessionId(), is("brokerSession"));
            assertThat(brokerSessions.get(0).getBrokerUserId(), is("brokerUserId"));

            UserSessionModel sessionByPredicate = s.sessions()
                    .getUserSessionWithPredicate(realm, session.getId(), false, s2 -> s2.getBrokerUserId()
                            .equals("brokerUserId"));
            assertThat(sessionByPredicate.getBrokerSessionId(), is("brokerSession"));
            assertThat(sessionByPredicate.getBrokerUserId(), is("brokerUserId"));

            return null;
        });

        withRealm(realmId, (s, realm) -> {
            UserModel testuser = s.users().getUserByUsername(realm, "user1");
            UserSessionModel session = s.sessions()
                    .createUserSession(
                            realm, testuser, "testuser", "127.0.0.1", "test", false, "brokerSession", "brokerUserId");
            s.sessions().createOfflineUserSession(session);

            List<UserSessionModel> brokerSessions = s.sessions()
                    .getOfflineUserSessionByBrokerUserIdStream(realm, "brokerUserId")
                    .collect(Collectors.toList());
            assertThat(brokerSessions, hasSize(1));
            assertThat(brokerSessions.get(0).getBrokerSessionId(), is("brokerSession"));
            assertThat(brokerSessions.get(0).getBrokerUserId(), is("brokerUserId"));

            UserSessionModel sessionByPredicate = s.sessions()
                    .getUserSessionWithPredicate(realm, session.getId(), true, s2 -> s2.getBrokerUserId()
                            .equals("brokerUserId"));
            assertThat(sessionByPredicate.getBrokerSessionId(), is("brokerSession"));
            assertThat(sessionByPredicate.getBrokerUserId(), is("brokerUserId"));

            return session.getId();
        });
    }

    @Test
    public void testActiveClientSessionStats() {
        withRealm(realmId, (s, r) -> {
            RealmModel realm = s.realms().getRealmByName("test");
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);

            ClientModel client1 = realm.getClientByClientId("test-app");
            ClientModel client2 = realm.getClientByClientId("third-party");

            // Create client1 session
            AuthenticatedClientSessionModel clientSession1 =
                    s.sessions().createClientSession(realm, client1, userSession);
            clientSession1.setAction("foo1");
            int currentTime1 = Time.currentTime();
            clientSession1.setTimestamp(currentTime1);

            // Create client2 session
            AuthenticatedClientSessionModel clientSession2 =
                    s.sessions().createClientSession(realm, client2, userSession);
            clientSession2.setAction("foo2");
            int currentTime2 = Time.currentTime();
            clientSession2.setTimestamp(currentTime2);

            return null;
        });

        withRealm(realmId, (s, r) -> {
            RealmModel realm = s.realms().getRealmByName("test");
            ClientModel client1 = realm.getClientByClientId("test-app");
            ClientModel client2 = realm.getClientByClientId("third-party");

            Map<String, Long> stats = s.sessions().getActiveClientSessionStats(realm, false);
            assertThat(stats.entrySet(), hasSize(2));
            assertThat(stats.get(client1.getId()), is(1L));
            assertThat(stats.get(client2.getId()), is(1L));

            return null;
        });
    }

    @Test
    public void testRemoveSessions() {
        String sessionId = withRealm(realmId, (s, realm) -> s.sessions()
                .createUserSession(
                        realm,
                        s.users().getUserByUsername(realm, "user1"),
                        "user1",
                        "127.0.0.2",
                        "form",
                        true,
                        null,
                        null)
                .getId());
        withRealm(realmId, (s, realm) -> s.clients().addClient(realm, "clientId"));

        withRealm(realmId, (s, realm) -> {
            assertNotNull(s.sessions().getUserSession(realm, sessionId));
            s.sessions().removeUserSessions(realm, s.users().getUserByUsername(realm, "user1"));

            return null;
        });

        String session2Id = withRealm(realmId, (s, realm) -> {
            assertNull(s.sessions().getUserSession(realm, sessionId));

            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);
            ClientModel client1 = realm.getClientByClientId("clientId");

            s.sessions().createClientSession(realm, client1, userSession);
            return userSession.getId();
        });

        withRealm(realmId, (s, realm) -> {
            assertNotNull(s.sessions().getUserSession(realm, session2Id));
            s.sessions().onClientRemoved(realm, realm.getClientByClientId("clientId"));

            return null;
        });

        String offlineSessionId = withRealm(realmId, (s, realm) -> {
            assertNull(s.sessions().getUserSession(realm, session2Id));

            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);
            return s.sessions().createOfflineUserSession(userSession).getId();
        });

        withRealm(realmId, (s, realm) -> {
            UserSessionModel offlineUserSession = s.sessions().getOfflineUserSession(realm, offlineSessionId);
            assertTrue(offlineUserSession.isOffline());

            s.sessions().removeOfflineUserSession(realm, offlineUserSession);

            return null;
        });

        String offlineSessionId2 = withRealm(realmId, (s, realm) -> {
            UserSessionModel offlineUserSession = s.sessions().getOfflineUserSession(realm, offlineSessionId);
            assertFalse(offlineUserSession.isOffline()); // Returned corresponding live session

            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);
            return s.sessions().createOfflineUserSession(userSession).getId();
        });

        withRealm(realmId, (s, realm) -> {
            UserSessionModel offlineUserSession = s.sessions().getOfflineUserSession(realm, offlineSessionId2);
            assertTrue(offlineUserSession.isOffline());

            s.sessions()
                    .removeOfflineUserSession(
                            realm,
                            s.sessions()
                                    .getUserSession(
                                            realm,
                                            offlineUserSession.getNote(UserSessionModel.CORRESPONDING_SESSION_ID)));

            return null;
        });

        withRealm(realmId, (s, realm) -> {
            UserSessionModel offlineUserSession = s.sessions().getOfflineUserSession(realm, offlineSessionId2);
            assertFalse(offlineUserSession.isOffline()); // Returned corresponding live session

            return null;
        });
    }

    @Test
    public void testImportUserSessions() {
        withRealm(realmId, (s, realm) -> s.clients().addClient(realm, "clientId"));
        UserSessionModel userSession1 = withRealm(realmId, (s, realm) -> {
            UserSessionModel model = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);
            s.sessions().createClientSession(realm, s.clients().getClientByClientId(realm, "clientId"), model);

            return model;
        });

        UserSessionModel userSession2 = withRealm(realmId, (s, realm) -> {
            UserSessionModel model = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user2"),
                            "user2",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);
            s.sessions().createClientSession(realm, s.clients().getClientByClientId(realm, "clientId"), model);

            return model;
        });

        withRealm(realmId, (s, realm) -> {
            s.sessions().removeUserSessions(realm);
            return null;
        });

        withRealm(realmId, (s, realm) -> {
            assertThat(
                    s.sessions()
                            .getUserSessionsStream(realm, s.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList()),
                    hasSize(0));
            assertThat(
                    s.sessions()
                            .getUserSessionsStream(realm, s.users().getUserByUsername(realm, "user2"))
                            .collect(Collectors.toList()),
                    hasSize(0));

            s.sessions().importUserSessions(Arrays.asList(userSession1, userSession2), false);
            return null;
        });

        withRealm(realmId, (s, realm) -> {
            assertThat(
                    s.sessions()
                            .getUserSessionsStream(realm, s.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList()),
                    hasSize(1));
            assertThat(
                    s.sessions()
                            .getUserSessionsStream(realm, s.users().getUserByUsername(realm, "user2"))
                            .collect(Collectors.toList()),
                    hasSize(1));
            Map<String, Long> stats = s.sessions().getActiveClientSessionStats(realm, false);
            assertThat(
                    stats.get(s.clients().getClientByClientId(realm, "clientId").getId()), is(2L));

            return null;
        });
    }
}
