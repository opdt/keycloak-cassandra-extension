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
package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.KeycloakModelTest;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.RequireProvider;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.keycloak.device.DeviceRepresentationProvider;
import org.keycloak.models.*;
import org.keycloak.services.managers.RealmManager;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 *
 * @author hmlnarik
 */
@RequireProvider(UserProvider.class)
@RequireProvider(RealmProvider.class)
@RequireProvider(UserSessionProvider.class)
@RequireProvider(DeviceRepresentationProvider.class)
public class OfflineSessionPersistenceTest extends KeycloakModelTest {

    private static final int USER_COUNT = 50;
    private static final int OFFLINE_SESSION_COUNT_PER_USER = 10;

    private String realmId;
    private List<String> userIds;

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = prepareRealm(s, "realm");
        this.realmId = realm.getId();

        userIds = IntStream.range(0, USER_COUNT)
            .mapToObj(i -> s.users().addUser(realm, "user-" + i))
            .map(UserModel::getId)
            .collect(Collectors.toList());
    }

    private static RealmModel prepareRealm(KeycloakSession s, String name) {
        RealmModel realm = createRealm(s, name);
        realm.setDefaultRole(s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
        realm.setSsoSessionMaxLifespan(10 * 60 * 60);
        realm.setSsoSessionIdleTimeout(1 * 60 * 60);
        realm.setOfflineSessionMaxLifespan(365 * 24 * 60 * 60);
        realm.setOfflineSessionIdleTimeout(30 * 24 * 60 * 60);
        return realm;
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        new RealmManager(s).removeRealm(s.realms().getRealm(realmId));  // See https://issues.redhat.com/browse/KEYCLOAK-17876
    }

    @Test
    public void testPersistenceSingleNodeDeleteRealm() {
        String realmId2 = inComittedTransaction(session -> { return prepareRealm(session, "realm2").getId(); });
        List<String> userIds2 = withRealm(realmId2, (session, realm) -> IntStream.range(0, USER_COUNT)
            .mapToObj(i -> session.users().addUser(realm, "user2-" + i))
            .map(UserModel::getId)
            .collect(Collectors.toList())
        );

        try {
            List<String> offlineSessionIds = createOfflineSessions(realmId, userIds);
            assertOfflineSessionsExist(realmId, offlineSessionIds);

            List<String> offlineSessionIds2 = createOfflineSessions(realmId2, userIds2);
            assertOfflineSessionsExist(realmId2, offlineSessionIds2);

            // Simulate server restart
            reinitializeKeycloakSessionFactory();

            withRealm(realmId2, (session, realm) -> new RealmManager(session).removeRealm(realm));

            // Simulate server restart
            reinitializeKeycloakSessionFactory();
            assertOfflineSessionsExist(realmId, offlineSessionIds);
        } finally {
            withRealm(realmId2, (session, realm) -> realm == null ? false : new RealmManager(session).removeRealm(realm));
        }
    }

    @Test
    public void testPersistenceSingleNode() {
        List<String> offlineSessionIds = createOfflineSessions(realmId, userIds);
        assertOfflineSessionsExist(realmId, offlineSessionIds);

        // Simulate server restart
        reinitializeKeycloakSessionFactory();
        assertOfflineSessionsExist(realmId, offlineSessionIds);
    }

    /**
     * Assert that all the offline sessions passed in the {@code offlineSessionIds} parameter exist
     */
    private void assertOfflineSessionsExist(String realmId, Collection<String> offlineSessionIds) {
        int foundOfflineSessions = withRealm(realmId, (session, realm) -> offlineSessionIds.stream()
            .map(offlineSessionId -> session.sessions().getOfflineUserSession(realm, offlineSessionId))
            .map(ous -> ous == null ? 0 : 1)
            .reduce(0, Integer::sum));

        assertThat(foundOfflineSessions, Matchers.is(offlineSessionIds.size()));
        // catch a programming error where an empty collection of offline session IDs is passed
        assertThat(foundOfflineSessions, Matchers.greaterThan(0));
    }

    // ***************** Helper methods *****************

    /**
     * Creates {@link #OFFLINE_SESSION_COUNT_PER_USER} offline sessions for every user from {@link #userIds}.
     * @return Ids of the offline sessions
     */
    private List<String> createOfflineSessions(String realmId, List<String> userIds) {
        return withRealm(realmId, (session, realm) ->
            userIds.stream()
                .flatMap(userId -> createOfflineSessions(session, realm, userId, us -> {}))
                .map(UserSessionModel::getId)
                .collect(Collectors.toList())
        );
    }

    /**
     * Creates {@link #OFFLINE_SESSION_COUNT_PER_USER} offline sessions for {@code userId} user.
     */
    private Stream<UserSessionModel> createOfflineSessions(KeycloakSession session, RealmModel realm, String userId, Consumer<? super UserSessionModel> alterUserSession) {
        return IntStream.range(0, OFFLINE_SESSION_COUNT_PER_USER)
            .mapToObj(sess -> createOfflineSession(session, realm, userId, sess))
            .peek(alterUserSession == null ? us -> {} : us -> alterUserSession.accept(us));
    }

    private UserSessionModel createOfflineSession(KeycloakSession session, RealmModel realm, String userId, int sessionIndex) {
        final UserModel user = session.users().getUserById(realm, userId);
        UserSessionModel us = session.sessions().createUserSession(realm, user, "un" + sessionIndex, "ip1", "auth", false, null, null);
        return session.sessions().createOfflineUserSession(us);
    }

}
