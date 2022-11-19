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

import de.arbeitsagentur.opdt.keycloak.cassandra.AbstractCassandraProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.AuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.cache.ThreadLocalCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Time;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.map.common.TimeAdapter;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.sessions.AuthenticationSessionCompoundId;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.map.common.ExpirationUtils.isExpired;
import static org.keycloak.models.utils.SessionExpiration.getAuthSessionLifespan;

@JBossLog
@RequiredArgsConstructor
public class CassandraAuthSessionProvider extends AbstractCassandraProvider implements AuthenticationSessionProvider {
  private final KeycloakSession session;
  private final AuthSessionRepository authSessionRepository;
  private final int authSessionsLimit;

  private Function<RootAuthenticationSession, RootAuthenticationSessionModel> entityToAdapterFunc(RealmModel realm) {
    return origEntity -> {
      if(origEntity == null) {
        return null;
      }

      if (isExpired(origEntity, true)) {
        authSessionRepository.deleteRootAuthSession(origEntity);
        return null;
      } else {
        return new CassandraRootAuthSessionAdapter(session, realm, origEntity, authSessionRepository, authSessionsLimit);
      }
    };
  }

  private Predicate<RootAuthenticationSession> entityRealmFilter(String realmId) {
    if (realmId == null) {
      return c -> false;
    }
    return entity -> Objects.equals(realmId, entity.getRealmId());
  }

  @Override
  public RootAuthenticationSessionModel createRootAuthenticationSession(RealmModel realm) {
    Objects.requireNonNull(realm, "The provided realm can't be null!");
    return createRootAuthenticationSession(realm, null);
  }

  @Override
  public RootAuthenticationSessionModel createRootAuthenticationSession(RealmModel realm, String id) {
    Objects.requireNonNull(realm, "The provided realm can't be null!");

    log.tracef("createRootAuthenticationSession(%s)%s", realm.getName(), getShortStackTrace());

    long timestamp = Time.currentTimeMillis();
    int authSessionLifespanSeconds = getAuthSessionLifespan(realm);
    RootAuthenticationSession entity = RootAuthenticationSession.builder()
        .id(id == null ? KeycloakModelUtils.generateId() : id)
        .realmId(realm.getId())
        .timestamp(timestamp)
        .expiration(timestamp + TimeAdapter.fromSecondsToMilliseconds(authSessionLifespanSeconds))
        .build();

    if (id != null && authSessionRepository.findRootAuthSessionById(id) != null) {
      throw new ModelDuplicateException("Root authentication session exists: " + entity.getId());
    }

    authSessionRepository.insertOrUpdate(entity);

    return entityToAdapterFunc(realm).apply(entity);
  }

  @Override
  public RootAuthenticationSessionModel getRootAuthenticationSession(RealmModel realm, String authenticationSessionId) {
    Objects.requireNonNull(realm, "The provided realm can't be null!");
    if (authenticationSessionId == null) {
      return null;
    }

    log.tracef("getRootAuthenticationSession(%s, %s)%s", realm.getName(), authenticationSessionId, getShortStackTrace());

    RootAuthenticationSession entity = authSessionRepository.findRootAuthSessionById(authenticationSessionId);
    return (entity == null || !entityRealmFilter(realm.getId()).test(entity))
        ? null
        : entityToAdapterFunc(realm).apply(entity);
  }

  @Override
  public void removeRootAuthenticationSession(RealmModel realm, RootAuthenticationSessionModel authenticationSession) {
    Objects.requireNonNull(authenticationSession, "The provided root authentication session can't be null!");
    authSessionRepository.deleteRootAuthSession(authenticationSession.getId());
  }

  @Override
  public void removeAllExpired() {
    log.tracef("removeAllExpired()%s", getShortStackTrace());
    log.warnf("Clearing expired entities should not be triggered manually. It is responsibility of the store to clear these.");
  }

  @Override
  public void removeExpired(RealmModel realm) {
    log.tracef("removeExpired(%s)%s", realm, getShortStackTrace());
    log.warnf("Clearing expired entities should not be triggered manually. It is responsibility of the store to clear these.");
  }

  @Override
  public void onRealmRemoved(RealmModel realm) {
    // Just let them expire...
  }

  @Override
  public void onClientRemoved(RealmModel realm, ClientModel client) {
    // Just let them expire...
  }

  @Override
  public void updateNonlocalSessionAuthNotes(AuthenticationSessionCompoundId compoundId, Map<String, String> authNotesFragment) {
    if (compoundId == null) {
      return;
    }
    Objects.requireNonNull(authNotesFragment, "The provided authentication's notes map can't be null!");
    AuthenticationSession authenticationSession = authSessionRepository.findAuthSessionsByParentSessionId(compoundId.getRootSessionId()).stream()
        .filter(s -> Objects.equals(s.getTabId(), compoundId.getTabId()))
        .filter(s -> Objects.equals(s.getClientId(), compoundId.getClientUUID()))
        .findFirst()
        .orElse(null);

    if(authenticationSession != null) {
      authenticationSession.setAuthNotes(authNotesFragment);
      authSessionRepository.insertOrUpdate(authenticationSession);
    }
  }

  @Override
  protected String getCacheName() {
    return ThreadLocalCache.AUTH_SESSION_CACHE;
  }
}
