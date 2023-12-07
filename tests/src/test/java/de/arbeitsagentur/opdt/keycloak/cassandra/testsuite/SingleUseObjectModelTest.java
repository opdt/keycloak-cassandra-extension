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

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.models.DefaultActionTokenKey;
import org.keycloak.common.util.Time;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserModel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;

public class SingleUseObjectModelTest extends KeycloakModelTest {

    private String realmId;

    private String userId;

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().createRealm("realm");
        realm.setDefaultRole(s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
        realmId = realm.getId();
        UserModel user = s.users().addUser(realm, "user");
        userId = user.getId();
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        Time.setOffset(0);
        s.realms().removeRealm(realmId);
    }

    @Test
    public void testActionTokens() {
        DefaultActionTokenKey key = withRealm(realmId, (session, realm) -> {
            SingleUseObjectProvider singleUseObjectProvider = session.singleUseObjects();
            int time = Time.currentTime();
            DefaultActionTokenKey actionTokenKey = new DefaultActionTokenKey(userId, UUID.randomUUID().toString(), time + 3, null);
            Map<String, String> notes = new HashMap<>();
            notes.put("foo", "bar");
            singleUseObjectProvider.put(actionTokenKey.serializeKey(), actionTokenKey.getExp() - time, notes);
            return actionTokenKey;
        });

        inComittedTransaction(session -> {
            SingleUseObjectProvider singleUseObjectProvider = session.singleUseObjects();
            Map<String, String> notes = singleUseObjectProvider.get(key.serializeKey());
            Assert.assertNotNull(notes);
            Assert.assertEquals("bar", notes.get("foo"));

            notes = singleUseObjectProvider.remove(key.serializeKey());
            Assert.assertNotNull(notes);
            Assert.assertEquals("bar", notes.get("foo"));
        });

        inComittedTransaction(session -> {
            SingleUseObjectProvider singleUseObjectProvider = session.singleUseObjects();
            Map<String, String> notes = singleUseObjectProvider.get(key.serializeKey());
            Assert.assertNull(notes);

            notes = new HashMap<>();
            notes.put("foo", "bar");
            singleUseObjectProvider.put(key.serializeKey(), key.getExp() - Time.currentTime(), notes);
        });

        inComittedTransaction(session -> {
            SingleUseObjectProvider singleUseObjectProvider = session.singleUseObjects();
            Map<String, String> notes = singleUseObjectProvider.get(key.serializeKey());
            Assert.assertNotNull(notes);
            Assert.assertEquals("bar", notes.get("foo"));

            sleep(5000);

            notes = singleUseObjectProvider.get(key.serializeKey());
            Assert.assertNull(notes);
        });
    }

    @Test
    public void testSingleUseStore() {
        String key = UUID.randomUUID().toString();
        Map<String, String> notes = new HashMap<>();
        notes.put("foo", "bar");

        Map<String, String> notes2 = new HashMap<>();
        notes2.put("baf", "meow");

        inComittedTransaction(session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Assert.assertFalse(singleUseStore.replace(key, notes2));

            singleUseStore.put(key,  3, notes);
        });

        inComittedTransaction(session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Map<String, String> actualNotes = singleUseStore.get(key);
            Assert.assertEquals(notes, actualNotes);

            Assert.assertTrue(singleUseStore.replace(key, notes2));
        });

        inComittedTransaction(session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Map<String, String> actualNotes = singleUseStore.get(key);
            Assert.assertEquals(notes2, actualNotes);

            Assert.assertFalse(singleUseStore.putIfAbsent(key, 3));

            Assert.assertEquals(notes2, singleUseStore.remove(key));
        });

        inComittedTransaction(session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Assert.assertTrue(singleUseStore.putIfAbsent(key, 3));
        });

        inComittedTransaction(session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Map<String, String> actualNotes = singleUseStore.get(key);
            assertThat(actualNotes, Matchers.anEmptyMap());

            sleep(5000);

            Assert.assertNull(singleUseStore.get(key));
        });
    }

    @Test
    public void testNullValueInNotes() {
        inComittedTransaction(session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Map<String, String> nullNotes = new HashMap<>();
            nullNotes.put("key1", null);
            singleUseStore.put("key", 5, nullNotes);

            Assert.assertNull(singleUseStore.get("key").get("key1"));
        });
    }

    private void sleep(int waitTimeMs) {
        try {
            Thread.sleep(waitTimeMs);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
