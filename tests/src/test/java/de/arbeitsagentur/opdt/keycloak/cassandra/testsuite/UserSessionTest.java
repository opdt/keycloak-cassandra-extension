/*
 * Copyright 2022 IT-Systemhaus der Bundesagentur fuer Arbeit
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
import org.keycloak.models.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@RequireProvider(UserProvider.class)
@RequireProvider(UserSessionProvider.class)
public class UserSessionTest extends KeycloakModelTest {
  private String realmId;
  private String userId;

  @Override
  public void createEnvironment(KeycloakSession s) {
    RealmModel realm = s.realms().createRealm("realm");
    realm.setSsoSessionMaxLifespan(60 * 20);
    realm.setSsoSessionIdleTimeout(60 * 20);
    realm.setClientSessionMaxLifespan(60 * 20);
    UserModel testuser = s.users().addUser(realm, "testuser");
    this.userId = testuser.getId();
    this.realmId = realm.getId();
  }

  @Override
  public void cleanEnvironment(KeycloakSession s) {
    RealmModel realm = s.realms().getRealm(realmId);
    UserModel user = s.users().getUserById(realm, userId);
    s.users().removeUser(realm, user);

    s.realms().removeRealm(realmId);
  }

  @Test
  public void testUserSessionNotes() {
    String sessionId = withRealm(realmId, (s, realm) -> {
      UserModel testuser = s.users().getUserById(realm, userId);
      UserSessionModel session = s.sessions().createUserSession(realm, testuser, "testuser", "127.0.0.1", "test", false, null, null);
      session.setNote("key1", "value1");
      session.setNote("key2", "value2");

      UserSessionModel newlyLoadedSession = s.sessions().getUserSession(realm, session.getId());
      newlyLoadedSession.setNote("key3", "value3");

      UserSessionModel currentSession = s.sessions().getUserSession(realm, session.getId());
      assertThat(currentSession.getNotes().entrySet(), hasSize(3));
      assertThat(currentSession.getNotes().get("key1"), equalTo("value1"));
      assertThat(currentSession.getNotes().get("key2"), equalTo("value2"));
      assertThat(currentSession.getNotes().get("key3"), equalTo("value3"));

      return session.getId();
    });

    // New transaction
    withRealm(realmId, (s, realm) -> {
      UserModel testuser = s.users().getUserById(realm, userId);
      UserSessionModel session = s.sessions().getUserSession(realm, sessionId);
      session.setNote("key4", "value4");

      UserSessionModel currentSession = s.sessions().getUserSession(realm, sessionId);
      assertThat(currentSession.getNotes().entrySet(), hasSize(4));
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
      UserModel testuser = s.users().getUserById(realm, userId);
      ClientModel client = s.clients().addClient(realm, "testclient");
      UserSessionModel session = s.sessions().createUserSession(realm, testuser, "testuser", "127.0.0.1", "test", false, null, null);
      session.setNote("key1", "value1");


      AuthenticatedClientSessionModel clientSession = s.sessions().createClientSession(realm, client, session);
      clientSession.setNote("ckey", "cval");

      session.setNote("key2", "value2");
      clientSession.getUserSession().setNote("key3", "value3");

      UserSessionModel currentSession = s.sessions().getUserSession(realm, session.getId());
      assertThat(currentSession.getNotes().entrySet(), hasSize(3));
      assertThat(currentSession.getNotes().get("key1"), equalTo("value1"));
      assertThat(currentSession.getNotes().get("key2"), equalTo("value2"));
      assertThat(currentSession.getNotes().get("key3"), equalTo("value3"));

      return session.getId();
    });
  }
}
