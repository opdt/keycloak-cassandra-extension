/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraUserSessionAdapter;
import org.junit.Test;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;

import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class UserSessionExpirationTest extends KeycloakModelTest {

    private String realmId;

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = createRealm(s, "test");
        realm.setDefaultRole(s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));

        s.users().addUser(realm, "user1").setEmail("user1@localhost");
        s.users().addUser(realm, "user2").setEmail("user2@localhost");

        this.realmId = realm.getId();
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

    @Test
    public void testClientSessionIdleTimeout() {

        // Set low ClientSessionIdleTimeout
        withRealm(realmId, (session, realm) -> {
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            realm.setClientSessionIdleTimeout(5);
            return null;
        });

        String uSId = withRealm(realmId, (session, realm) -> session.sessions().createUserSession(realm, session.users().getUserByUsername(realm, "user1"), "user1", "127.0.0.1", "form", true, null, null).getId());

        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        Time.setOffset(3);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        Time.setOffset(5);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), nullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(0));
    }

    @Test
    public void testClientSessionIdleTimeoutOverride() {

        // Set low ClientSessionIdleTimeout
        withRealm(realmId, (session, realm) -> {
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            realm.setClientSessionIdleTimeout(10);
            return null;
        });

        String uSId = withRealm(realmId, (session, realm) -> session.sessions().createUserSession(realm, session.users().getUserByUsername(realm, "user1"), "user1", "127.0.0.1", "form", true, null, null).getId());

        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        Time.setOffset(3);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        withRealm(realmId, (session, realm) -> {
            session.sessions().getUserSession(realm, uSId).setNote(CassandraUserSessionAdapter.CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE, "5");
            return null;
        });

        Time.setOffset(6);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        Time.setOffset(8);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), nullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(0));
    }

    @Test
    public void testClientSessionIdleTimeoutOverrideGreaterThanOldValue() {

        // Set low ClientSessionIdleTimeout
        withRealm(realmId, (session, realm) -> {
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            realm.setClientSessionIdleTimeout(10);
            return null;
        });

        String uSId = withRealm(realmId, (session, realm) -> session.sessions().createUserSession(realm, session.users().getUserByUsername(realm, "user1"), "user1", "127.0.0.1", "form", true, null, null).getId());

        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        Time.setOffset(3);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        withRealm(realmId, (session, realm) -> {
            session.sessions().getUserSession(realm, uSId).setNote(CassandraUserSessionAdapter.CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE, "5");
            return null;
        });

        // Should do nothing, since new override is greater than old override
        withRealm(realmId, (session, realm) -> {
            session.sessions().getUserSession(realm, uSId).setNote(CassandraUserSessionAdapter.CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE, "7");
            return null;
        });

        Time.setOffset(6);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        Time.setOffset(8);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), nullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(0));
    }

    @Test
    public void testClientSessionIdleTimeoutOverrideGreaterThanOldRealmValue() {

        // Set low ClientSessionIdleTimeout
        withRealm(realmId, (session, realm) -> {
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            realm.setClientSessionIdleTimeout(5);
            return null;
        });

        String uSId = withRealm(realmId, (session, realm) -> session.sessions().createUserSession(realm, session.users().getUserByUsername(realm, "user1"), "user1", "127.0.0.1", "form", true, null, null).getId());

        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        Time.setOffset(3);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        // Should do nothing, since new override is greater than old override
        withRealm(realmId, (session, realm) -> {
            session.sessions().getUserSession(realm, uSId).setNote(CassandraUserSessionAdapter.CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE, "7");
            return null;
        });

        Time.setOffset(6);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        Time.setOffset(8);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), nullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(0));
    }

    @Test
    public void testClientSessionIdleTimeoutOverrideTtl() throws InterruptedException {

        // Set low ClientSessionIdleTimeout
        withRealm(realmId, (session, realm) -> {
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            realm.setClientSessionIdleTimeout(10);
            return null;
        });

        String uSId = withRealm(realmId, (session, realm) -> session.sessions().createUserSession(realm, session.users().getUserByUsername(realm, "user1"), "user1", "127.0.0.1", "form", true, null, null).getId());

        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        Thread.sleep(3000);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        withRealm(realmId, (session, realm) -> {
            session.sessions().getUserSession(realm, uSId).setNote(CassandraUserSessionAdapter.CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE, "5");
            return null;
        });

        Thread.sleep(3000);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        Thread.sleep(2000);
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), nullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(0));
    }

    @Test
    public void testDeleteSession() {
        withRealm(realmId, (session, realm) -> {
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            realm.setClientSessionIdleTimeout(5);
            return null;
        });

        String uSId = withRealm(realmId, (session, realm) -> session.sessions().createUserSession(realm, session.users().getUserByUsername(realm, "user1"), "user1", "127.0.0.1", "form", true, null, null).getId());

        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), notNullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(1));

        withRealm(realmId, (session, realm) -> {
            UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);
            session.sessions().removeUserSession(realm, userSession);
            return null;
        });

        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSession(realm, uSId)), nullValue());
        assertThat(withRealm(realmId, (session, realm) -> session.sessions().getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1")).collect(Collectors.toList())), hasSize(0));
    }
}
