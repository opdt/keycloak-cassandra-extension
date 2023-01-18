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
package de.arbeitsagentur.opdt.keycloak.cassandra.authSession;

import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.AuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.CassandraModelTransaction;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Base64Url;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.common.util.Time;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.map.common.TimeAdapter;
import org.keycloak.models.utils.SessionExpiration;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.keycloak.models.utils.SessionExpiration.getAuthSessionLifespan;

@JBossLog
@EqualsAndHashCode(of = "rootAuthenticationSession")
@RequiredArgsConstructor
public class CassandraRootAuthSessionAdapter implements RootAuthenticationSessionModel {
    private final KeycloakSession session;
    private final RealmModel realm;
    private final RootAuthenticationSession rootAuthenticationSession;
    private final AuthSessionRepository authSessionRepository;

    private final int authSessionsLimit;

    private Map<String, CassandraAuthSessionAdapter> sessionModels = new HashMap<>();
    private boolean updated = false;

    private static final Comparator<AuthenticationSession> TIMESTAMP_COMPARATOR = Comparator.comparingLong(AuthenticationSession::getTimestamp);

    private Function<AuthenticationSession, CassandraAuthSessionAdapter> entityToAdapterFunc(RealmModel realm) {
        return (origEntity) -> {
            if (origEntity == null) {
                return null;
            }

            if (sessionModels.containsKey(origEntity.getTabId())) {
                return sessionModels.get(origEntity.getTabId());
            }

            CassandraAuthSessionAdapter adapter = new CassandraAuthSessionAdapter(session, realm, this, origEntity, authSessionRepository);
            session.getTransactionManager().enlistAfterCompletion((CassandraModelTransaction) adapter::flush);
            sessionModels.put(adapter.getTabId(), adapter);

            return adapter;
        };
    }

    @Override
    public String getId() {
        return rootAuthenticationSession.getId();
    }

    @Override
    public RealmModel getRealm() {
        return realm;
    }

    @Override
    public int getTimestamp() {
        return TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(TimeAdapter.fromMilliSecondsToSeconds(rootAuthenticationSession.getTimestamp()));
    }

    @Override
    public void setTimestamp(int timestamp) {
        rootAuthenticationSession.setTimestamp(TimeAdapter.fromSecondsToMilliseconds(timestamp));
        rootAuthenticationSession.setExpiration(TimeAdapter.fromSecondsToMilliseconds(SessionExpiration.getAuthSessionExpiration(realm, timestamp)));

        updated = true;
    }

    @Override
    public Map<String, AuthenticationSessionModel> getAuthenticationSessions() {
        return authSessionRepository.findAuthSessionsByParentSessionId(rootAuthenticationSession.getId()).stream()
            .map(entityToAdapterFunc(realm))
            .collect(Collectors.toMap(CassandraAuthSessionAdapter::getTabId, Function.identity()));
    }

    @Override
    public AuthenticationSessionModel getAuthenticationSession(ClientModel client, String tabId) {
        if (client == null || tabId == null) {
            return null;
        }


        return authSessionRepository.findAuthSessionsByParentSessionId(rootAuthenticationSession.getId()).stream()
            .filter(s -> Objects.equals(s.getClientId(), client.getId()))
            .filter(s -> Objects.equals(s.getTabId(), tabId))
            .map(entityToAdapterFunc(realm))
            .findFirst()
            .orElse(null);
    }

    @Override
    public AuthenticationSessionModel createAuthenticationSession(ClientModel client) {
        Objects.requireNonNull(client, "The provided client can't be null!");

        List<AuthenticationSession> authenticationSessions = authSessionRepository.findAuthSessionsByParentSessionId(rootAuthenticationSession.getId());
        if (authenticationSessions != null && authenticationSessions.size() >= authSessionsLimit) {
            Optional<AuthenticationSession> oldest = authenticationSessions.stream().min(TIMESTAMP_COMPARATOR);
            String tabId = oldest.map(AuthenticationSession::getTabId).orElse(null);

            if (tabId != null) {
                log.debugf("Reached limit (%s) of active authentication sessions per a root authentication session. Removing oldest authentication session with TabId %s.", authSessionsLimit, tabId);

                // remove the oldest authentication session
                authSessionRepository.deleteAuthSession(oldest.get());
            }
        }

        long timestamp = Time.currentTimeMillis();
        int authSessionLifespanSeconds = getAuthSessionLifespan(realm);

        AuthenticationSession authSession = AuthenticationSession.builder()
            .parentSessionId(rootAuthenticationSession.getId())
            .clientId(client.getId())
            .timestamp(timestamp)
            .tabId(generateTabId())
            .build();

        rootAuthenticationSession.setTimestamp(timestamp);
        rootAuthenticationSession.setExpiration(timestamp + TimeAdapter.fromSecondsToMilliseconds(authSessionLifespanSeconds));

        authSessionRepository.insertOrUpdate(authSession, rootAuthenticationSession); // Set TTL from parent, TODO: this means an additional update-op...
        updated = true;

        CassandraAuthSessionAdapter cassandraAuthSessionAdapter = entityToAdapterFunc(realm).apply(authSession);
        session.getContext().setAuthenticationSession(cassandraAuthSessionAdapter);

        return cassandraAuthSessionAdapter;
    }

    @Override
    public void removeAuthenticationSessionByTabId(String tabId) {
        List<AuthenticationSession> allAuthSessions = authSessionRepository.findAuthSessionsByParentSessionId(rootAuthenticationSession.getId());
        AuthenticationSession toDelete = allAuthSessions.stream()
            .filter(s -> Objects.equals(s.getTabId(), tabId))
            .findFirst()
            .orElse(null);

        authSessionRepository.deleteAuthSession(toDelete);
        sessionModels.remove(tabId);
        if (toDelete != null) {
            if (allAuthSessions.size() == 1) {
                session.authenticationSessions().removeRootAuthenticationSession(realm, this);
            } else {
                long timestamp = Time.currentTimeMillis();
                rootAuthenticationSession.setTimestamp(timestamp);
                int authSessionLifespanSeconds = getAuthSessionLifespan(realm);
                rootAuthenticationSession.setExpiration(timestamp + TimeAdapter.fromSecondsToMilliseconds(authSessionLifespanSeconds));
                updated = true;
            }
        }
    }

    @Override
    public void restartSession(RealmModel realm) {
        authSessionRepository.deleteAuthSessions(rootAuthenticationSession.getId());
        sessionModels.clear();
        long timestamp = Time.currentTimeMillis();
        rootAuthenticationSession.setTimestamp(timestamp);
        int authSessionLifespanSeconds = getAuthSessionLifespan(realm);
        rootAuthenticationSession.setExpiration(timestamp + TimeAdapter.fromSecondsToMilliseconds(authSessionLifespanSeconds));
        updated = true;
    }

    private String generateTabId() {
        return Base64Url.encode(SecretGenerator.getInstance().randomBytes(8));
    }

    public void flush() {
        if (updated) {
            authSessionRepository.insertOrUpdate(rootAuthenticationSession);
            updated = false;
        }
    }
}
