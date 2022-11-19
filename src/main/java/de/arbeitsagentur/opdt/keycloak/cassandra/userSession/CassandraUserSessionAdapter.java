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
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSessionValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSession;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;
import org.keycloak.models.map.common.TimeAdapter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraSessionExpiration.setUserSessionExpiration;
import static org.keycloak.models.map.common.ExpirationUtils.isExpired;


@EqualsAndHashCode(of = "userSessionEntity")
@AllArgsConstructor
public class CassandraUserSessionAdapter implements UserSessionModel {
  private final KeycloakSession session;
  private final RealmModel realm;
  private UserSession userSessionEntity;
  private final UserSessionRepository userSessionRepository;

  public UserSession getUserSessionEntity() {
    return userSessionEntity;
  }

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
    Boolean rememberMe = userSessionEntity.getRememberMe();
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
    Boolean offline = userSessionEntity.getOffline();
    return offline != null ? offline : false;
  }

  @Override
  public Map<String, AuthenticatedClientSessionModel> getAuthenticatedClientSessions() {
    List<AuthenticatedClientSessionValue> authenticatedClientSessions = new ArrayList<>(userSessionEntity.getClientSessions().values());

    return authenticatedClientSessions
        .stream()
        .filter(Objects::nonNull)
        .filter(this::filterAndRemoveExpiredClientSessions)
        .filter(this::matchingOfflineFlag)
        .filter(this::filterAndRemoveClientSessionWithoutClient)
        .collect(Collectors.toMap(AuthenticatedClientSessionValue::getClientId, this::clientSessionEntityToModel));
  }

  @Override
  public void removeAuthenticatedClientSessions(Collection<String> removedClientUUIDS) {
    removedClientUUIDS.forEach(clientId -> userSessionEntity.getClientSessions().remove(clientId));
    userSessionRepository.insertOrUpdate(userSessionEntity);
  }

  @Override
  public String getNote(String name) {
    return userSessionEntity.getNotes().get(name);
  }

  @Override
  public void setNote(String name, String value) {
    if (value == null) {
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

    userSessionEntity.setRealmId(realm.getId());
    userSessionEntity.setUserId(user.getId());
    userSessionEntity.setLoginUsername(loginUsername);
    userSessionEntity.setIpAddress(ipAddress);
    userSessionEntity.setAuthMethod(authMethod);
    userSessionEntity.setRememberMe(rememberMe);
    userSessionEntity.setBrokerSessionId(brokerSessionId);
    userSessionEntity.setBrokerUserId(brokerUserId);
    userSessionEntity.setTimestamp(Time.currentTimeMillis());
    userSessionEntity.setLastSessionRefresh(Time.currentTimeMillis());
    userSessionEntity.setState(null);
    userSessionEntity.setNotes(new ConcurrentHashMap<>());
    userSessionEntity.setClientSessions(new ConcurrentHashMap<>());

    if (correspondingSessionId != null) {
      userSessionEntity.getNotes().put(CORRESPONDING_SESSION_ID, correspondingSessionId);
    }

    userSessionRepository.insertOrUpdate(userSessionEntity);
  }

  private boolean filterAndRemoveExpiredClientSessions(AuthenticatedClientSessionValue clientSession) {
    try {
      if (isExpired(clientSession, false)) {
        userSessionEntity.getClientSessions().remove(clientSession.getClientId());
        userSessionRepository.insertOrUpdate(userSessionEntity);
        return false;
      }
    } catch (ModelIllegalStateException ex) {
      userSessionEntity.getClientSessions().remove(clientSession.getClientId());
      userSessionRepository.insertOrUpdate(userSessionEntity);
      return false;
    }

    return true;
  }

  private boolean matchingOfflineFlag(AuthenticatedClientSessionValue clientSession) {
    Boolean isClientSessionOffline = clientSession.isOffline();

    // If client session doesn't have offline flag default to false
    if (isClientSessionOffline == null) return !isOffline();

    return isOffline() == isClientSessionOffline;
  }

  private boolean filterAndRemoveClientSessionWithoutClient(AuthenticatedClientSessionValue clientSession) {
    ClientModel client = realm.getClientById(clientSession.getClientId());

    if (client == null) {
      userSessionEntity.getClientSessions().remove(clientSession.getClientId());
      userSessionRepository.insertOrUpdate(userSessionEntity);

      // Filter out entities that doesn't have client
      return false;
    }

    // client session has client so we do not filter it out
    return true;
  }

  private AuthenticatedClientSessionModel clientSessionEntityToModel(AuthenticatedClientSessionValue clientSessionEntity) {
    return new CassandraAuthenticatedClientSessionAdapter(session, realm, this, clientSessionEntity, userSessionRepository) {
      @Override
      public void detachFromUserSession() {
        // TODO: what are the intended semantics of "detach"?
        userSessionEntity.getClientSessions().remove(clientSessionEntity.getClientId());
        userSessionRepository.insertOrUpdate(userSessionEntity);

        this.userSession = null;
      }
    };
  }
}
