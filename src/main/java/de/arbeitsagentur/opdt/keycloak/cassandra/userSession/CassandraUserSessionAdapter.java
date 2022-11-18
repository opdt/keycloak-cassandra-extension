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
package de.arbeitsagentur.opdt.keycloak.cassandra.userSession;

import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.UserSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSession;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;
import org.keycloak.models.map.common.TimeAdapter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraSessionExpiration.setUserSessionExpiration;
import static org.keycloak.models.map.common.ExpirationUtils.isExpired;


@EqualsAndHashCode(of = "userSessionEntity")
@RequiredArgsConstructor
public class CassandraUserSessionAdapter implements UserSessionModel {
  private final KeycloakSession session;
  private final RealmModel realm;
  private final UserSession userSessionEntity;
  private final UserSessionRepository userSessionRepository;

  @Override
  public String getId() {
    return userSessionEntity.getId();
  }

  @Override
  public RealmModel getRealm() {
    return realm;
  }

  @Override
  public String getBrokerSessionId() {
    return userSessionEntity.getBrokerSessionId();
  }

  @Override
  public String getBrokerUserId() {
    return userSessionEntity.getBrokerUserId();
  }

  @Override
  public UserModel getUser() {
    return session.users().getUserById(getRealm(), userSessionEntity.getUserId());
  }

  @Override
  public String getLoginUsername() {
    return userSessionEntity.getLoginUsername();
  }

  @Override
  public String getIpAddress() {
    return userSessionEntity.getIpAddress();
  }

  @Override
  public String getAuthMethod() {
    return userSessionEntity.getAuthMethod();
  }

  @Override
  public boolean isRememberMe() {
    Boolean rememberMe = userSessionEntity.isRememberMe();
    return rememberMe != null ? rememberMe : false;
  }

  @Override
  public int getStarted() {
    Long started = userSessionEntity.getTimestamp();
    return started != null ? TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(TimeAdapter.fromMilliSecondsToSeconds(started)) : 0;
  }

  @Override
  public int getLastSessionRefresh() {
    Long lastSessionRefresh = userSessionEntity.getLastSessionRefresh();
    return lastSessionRefresh != null ? TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(TimeAdapter.fromMilliSecondsToSeconds(lastSessionRefresh)) : 0;
  }

  @Override
  public void setLastSessionRefresh(int seconds) {
    userSessionEntity.setLastSessionRefresh(TimeAdapter.fromSecondsToMilliseconds(seconds));

    // whenever the lastSessionRefresh is changed recompute the expiration time
    setUserSessionExpiration(userSessionEntity, realm);
    userSessionRepository.insertOrUpdate(userSessionEntity);
  }

  @Override
  public boolean isOffline() {
    Boolean offline = userSessionEntity.isOffline();
    return offline != null ? offline : false;
  }

  @Override
  public Map<String, AuthenticatedClientSessionModel> getAuthenticatedClientSessions() {
    List<AuthenticatedClientSession> authenticatedClientSessions = userSessionRepository.findClientSessionsByUserSessionId(userSessionEntity.getId());
    if (authenticatedClientSessions == null) {
      return Collections.emptyMap();
    }

    return authenticatedClientSessions
        .stream()
        .filter(this::filterAndRemoveExpiredClientSessions)
        .filter(this::matchingOfflineFlag)
        .filter(this::filterAndRemoveClientSessionWithoutClient)
        .collect(Collectors.toMap(AuthenticatedClientSession::getClientId, this::clientSessionEntityToModel));
  }

  @Override
  public void removeAuthenticatedClientSessions(Collection<String> removedClientUUIDS) {
    List<AuthenticatedClientSession> authenticatedClientSessions = userSessionRepository.findClientSessionsByUserSessionId(userSessionEntity.getId());
    authenticatedClientSessions.stream()
        .filter(s -> removedClientUUIDS.contains(s.getClientId()))
        .forEach(userSessionRepository::deleteClientSession);
  }

  @Override
  public String getNote(String name) {
    return userSessionEntity.getNotes().get(name);
  }

  @Override
  public void setNote(String name, String value) {
    if(value == null) {
      removeNote(name);
      return;
    }

    userSessionEntity.getNotes().put(name, value);
    userSessionRepository.insertOrUpdate(userSessionEntity);
  }

  @Override
  public void removeNote(String name) {
    userSessionEntity.getNotes().remove(name);
    userSessionRepository.insertOrUpdate(userSessionEntity);
  }

  @Override
  public Map<String, String> getNotes() {
    return userSessionEntity.getNotes();
  }

  @Override
  public State getState() {
    return userSessionEntity.getState();
  }

  @Override
  public void setState(State state) {
    userSessionEntity.setState(state);
    userSessionRepository.insertOrUpdate(userSessionEntity);
  }

  @Override
  public void restartSession(RealmModel realm, UserModel user, String loginUsername, String ipAddress, String authMethod, boolean rememberMe, String brokerSessionId, String brokerUserId) {
    String correspondingSessionId = userSessionEntity.getNotes().get(CORRESPONDING_SESSION_ID);

    UserSession newSession = userSessionEntity.toBuilder()
        .realmId(realm.getId())
        .userId(user.getId())
        .loginUsername(loginUsername)
        .ipAddress(ipAddress)
        .authMethod(authMethod)
        .rememberMe(rememberMe)
        .brokerSessionId(brokerSessionId)
        .brokerUserId(brokerUserId)
        .timestamp(Time.currentTimeMillis())
        .lastSessionRefresh(Time.currentTimeMillis())
        .state(null)
        .notes(new ConcurrentHashMap<>())
        .build();

    if (correspondingSessionId != null)
      newSession.getNotes().put(CORRESPONDING_SESSION_ID, correspondingSessionId);

    // TODO: re-calc expiration (isnt done in MapUserSessionAdapter...)?
    userSessionRepository.findClientSessionsByUserSessionId(userSessionEntity.getId()).forEach(userSessionRepository::deleteClientSession);
    userSessionRepository.insertOrUpdate(newSession);
  }

  private boolean filterAndRemoveExpiredClientSessions(AuthenticatedClientSession clientSession) {
    try {
      if (isExpired(clientSession, false)) {
        userSessionRepository.deleteClientSession(clientSession);
        return false;
      }
    } catch (ModelIllegalStateException ex) {
      userSessionRepository.deleteClientSession(clientSession);
      return false;
    }

    return true;
  }

  private boolean matchingOfflineFlag(AuthenticatedClientSession clientSession) {
    Boolean isClientSessionOffline = clientSession.isOffline();

    // If client session doesn't have offline flag default to false
    if (isClientSessionOffline == null) return !isOffline();

    return isOffline() == isClientSessionOffline;
  }

  private boolean filterAndRemoveClientSessionWithoutClient(AuthenticatedClientSession clientSession) {
    ClientModel client = realm.getClientById(clientSession.getClientId());

    if (client == null) {
      userSessionRepository.deleteClientSession(clientSession);

      // Filter out entities that doesn't have client
      return false;
    }

    // client session has client so we do not filter it out
    return true;
  }

  private AuthenticatedClientSessionModel clientSessionEntityToModel(AuthenticatedClientSession clientSessionEntity) {
    return new CassandraAuthenticatedClientSessionAdapter(session, realm, this, clientSessionEntity, userSessionRepository) {
      @Override
      public void detachFromUserSession() {
        // TODO: what are the intended semantics of "detach"?
        userSessionRepository.deleteClientSession(clientSessionEntity);
        this.userSession = null;
      }
    };
  }
}
