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
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Time;
import org.keycloak.device.DeviceActivityManager;
import org.keycloak.models.*;
import org.keycloak.models.map.common.TimeAdapter;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraSessionExpiration.setClientSessionExpiration;
import static de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraSessionExpiration.setUserSessionExpiration;
import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.UserSessionModel.CORRESPONDING_SESSION_ID;
import static org.keycloak.models.UserSessionModel.SessionPersistenceState.TRANSIENT;
import static org.keycloak.models.map.common.ExpirationUtils.isExpired;

@JBossLog
@RequiredArgsConstructor
public class CassandraUserSessionProvider implements UserSessionProvider {
  private final KeycloakSession session;
  private final UserSessionRepository userSessionRepository;

  private final Map<String, UserSession> transientUserSessions = new HashMap<>();

  private Function<UserSession, CassandraUserSessionAdapter> entityToAdapterFunc(RealmModel realm) {
    // Clone entity before returning back, to avoid giving away a reference to the live object to the caller
    return (origEntity) -> {
      if (origEntity == null) return null;
      if (isExpired(origEntity, false)) {
        if (TRANSIENT == origEntity.getPersistenceState()) {
          transientUserSessions.remove(origEntity.getId());
        } else {
          userSessionRepository.deleteUserSession(origEntity);
        }
        return null;
      } else {
        return new CassandraUserSessionAdapter(session, realm, origEntity, userSessionRepository);
      }
    };
  }

  @Override
  public KeycloakSession getKeycloakSession() {
    return session;
  }

  @Override
  public AuthenticatedClientSessionModel createClientSession(RealmModel realm, ClientModel client, UserSessionModel userSession) {
    log.tracef("createClientSession(%s, %s, %s)%s", realm, client, userSession, getShortStackTrace());

    UserSession userSessionEntity = getUserSessionById(userSession.getId());

    if (userSessionEntity == null) {
      throw new IllegalStateException("User session entity does not exist: " + userSession.getId());
    }

    AuthenticatedClientSession entity = createAuthenticatedClientSessionEntityInstance(null, userSession.getId(),
        realm.getId(), client.getId(), false);
    String started = entity.getTimestamp() != null ? String.valueOf(TimeAdapter.fromMilliSecondsToSeconds(entity.getTimestamp())) : String.valueOf(0);
    entity.getNotes().put(AuthenticatedClientSessionModel.STARTED_AT_NOTE, started);
    setClientSessionExpiration(entity, realm, client);

    userSessionRepository.insertOrUpdate(userSession.getId(), entity);

    // We need to load the clientSession through userModel so we return an entity that is included within the
    // transaction and also, so we not avoid all the checks present in the adapter, for example expiration
    UserSessionModel userSessionModel = entityToAdapterFunc(realm).apply(userSessionEntity);
    return userSessionModel == null ? null : userSessionModel.getAuthenticatedClientSessionByClient(client.getId());
  }

  @Override
  public AuthenticatedClientSessionModel getClientSession(UserSessionModel userSession, ClientModel client, String clientSessionId, boolean offline) {
    log.tracef("getClientSession(%s, %s, %s, %s)%s", userSession, client, clientSessionId, offline, getShortStackTrace());

    return userSession.getAuthenticatedClientSessionByClient(client.getId());
  }

  @Override
  public UserSessionModel createUserSession(RealmModel realm, UserModel user, String loginUsername, String ipAddress, String authMethod, boolean rememberMe, String brokerSessionId, String brokerUserId) {
    return createUserSession(null, realm, user, loginUsername, ipAddress, authMethod, rememberMe, brokerSessionId,
        brokerUserId, UserSessionModel.SessionPersistenceState.PERSISTENT);
  }

  @Override
  public UserSessionModel createUserSession(String id, RealmModel realm, UserModel user, String loginUsername, String ipAddress, String authMethod, boolean rememberMe, String brokerSessionId, String brokerUserId, UserSessionModel.SessionPersistenceState persistenceState) {
    log.tracef("createUserSession(%s, %s, %s, %s)%s", id, realm, loginUsername, persistenceState, getShortStackTrace());

    UserSession entity = createUserSessionEntityInstance(id, realm.getId(), user.getId(), loginUsername, ipAddress, authMethod,
        rememberMe, brokerSessionId, brokerUserId, false);

    if (TRANSIENT == persistenceState) {
      if (id == null) {
        entity.setId(UUID.randomUUID().toString());
      }
      transientUserSessions.put(entity.getId(), entity);
    } else {
      if (id != null && userSessionRepository.findUserSessionById(id) != null) {
        throw new ModelDuplicateException("User session exists: " + id);
      }
      userSessionRepository.insertOrUpdate(entity);
    }

    entity.setPersistenceState(persistenceState);
    setUserSessionExpiration(entity, realm);
    UserSessionModel userSession = entityToAdapterFunc(realm).apply(entity);

    if (userSession != null) {
      DeviceActivityManager.attachDevice(userSession, session);
    }

    return userSession;
  }

  @Override
  public CassandraUserSessionAdapter getUserSession(RealmModel realm, String id) {
    Objects.requireNonNull(realm, "The provided realm can't be null!");

    log.tracef("getUserSession(%s, %s)%s", realm, id, getShortStackTrace());

    if (id == null) return null;

    UserSession userSessionEntity = transientUserSessions.get(id);
    if (userSessionEntity != null) {
      return entityToAdapterFunc(realm).apply(userSessionEntity);
    }

    UserSession userSessionById = userSessionRepository.findUserSessionById(id);
    if (userSessionById == null) {
      return null;
    }

    return entityToAdapterFunc(realm).apply(userSessionById);
  }

  @Override
  public Stream<UserSessionModel> getUserSessionsStream(RealmModel realm, UserModel user) {
    log.tracef("getUserSessionsStream(%s, %s)%s", realm, user, getShortStackTrace());

    return userSessionRepository.findUserSessionsByUserId(user.getId()).stream()
        .filter(s -> s.getRealmId().equals(realm.getId()))
        .filter(s -> !s.isOffline())
        .map(entityToAdapterFunc((realm)));
  }

  @Override
  public Stream<UserSessionModel> getUserSessionsStream(RealmModel realm, ClientModel client) {
    log.tracef("getUserSessionsStream(%s, %s)%s", realm, client, getShortStackTrace());

    return userSessionRepository.findUserSessionsByClientId(client.getId()).stream()
        .filter(s -> s.getRealmId().equals(realm.getId()))
        .filter(s -> !s.isOffline())
        .map(entityToAdapterFunc((realm)));
  }

  @Override
  public Stream<UserSessionModel> getUserSessionsStream(RealmModel realm, ClientModel client, Integer firstResult, Integer maxResults) {
    log.tracef("getUserSessionsStream(%s, %s, %s, %s)%s", realm, client, firstResult, maxResults, getShortStackTrace());

    // TODO: perf
    return getUserSessionsStream(realm, client)
        .filter(s -> s.getRealm().equals(realm))
        .filter(s -> !s.isOffline())
        .skip(firstResult)
        .limit(maxResults);
  }

  @Override
  public Stream<UserSessionModel> getUserSessionByBrokerUserIdStream(RealmModel realm, String brokerUserId) {
    log.tracef("getUserSessionByBrokerUserIdStream(%s, %s)%s", realm, brokerUserId, getShortStackTrace());

    return userSessionRepository.findUserSessionsByBrokerUserId(brokerUserId).stream()
        .filter(s -> s.getRealmId().equals(realm.getId()))
        .filter(s -> !s.isOffline())
        .map(entityToAdapterFunc((realm)));
  }

  @Override
  public UserSessionModel getUserSessionByBrokerSessionId(RealmModel realm, String brokerSessionId) {
    log.tracef("getUserSessionByBrokerSessionId(%s, %s)%s", realm, brokerSessionId, getShortStackTrace());

    return userSessionRepository.findUserSessionsByBrokerSession(brokerSessionId).stream()
        .filter(s -> s.getRealmId().equals(realm.getId()))
        .filter(s -> !s.isOffline())
        .map(entityToAdapterFunc((realm)))
        .findFirst()
        .orElse(null);
  }

  @Override
  public UserSessionModel getUserSessionWithPredicate(RealmModel realm, String id, boolean offline, Predicate<UserSessionModel> predicate) {
    log.tracef("getUserSessionWithPredicate(%s, %s, %s)%s", realm, id, offline, getShortStackTrace());

    Stream<CassandraUserSessionAdapter> userSessionEntityStream;
    if (offline) {
      userSessionEntityStream = getOfflineUserSessionEntityStream(realm, id)
          .map(entityToAdapterFunc(realm)).filter(Objects::nonNull);
    } else {
      CassandraUserSessionAdapter userSession = getUserSession(realm, id);
      userSessionEntityStream = userSession != null ? Stream.of(userSession) : Stream.empty();
    }

    return userSessionEntityStream
        .filter(predicate)
        .findFirst()
        .orElse(null);
  }

  @Override
  public long getActiveUserSessions(RealmModel realm, ClientModel client) {
    log.tracef("getActiveUserSessions(%s, %s)%s", realm, client, getShortStackTrace());

    // TODO: perf?!
    return userSessionRepository.findUserSessionsByClientId(client.getId()).size();
  }

  @Override
  public Map<String, Long> getActiveClientSessionStats(RealmModel realm, boolean offline) {
    log.tracef("getActiveClientSessionStats(%s, %s)%s", realm, offline, getShortStackTrace());

    // TODO: perf?!
    return userSessionRepository.findAll().stream()
        .filter(s -> s.getRealmId().equals(realm.getId()))
        .filter(s -> s.isOffline() == offline)
        .map(entityToAdapterFunc(realm))
        .filter(Objects::nonNull)
        .map(UserSessionModel::getAuthenticatedClientSessions)
        .map(Map::keySet)
        .flatMap(Collection::stream)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
  }

  @Override
  public void removeUserSession(RealmModel realm, UserSessionModel session) {
    Objects.requireNonNull(session, "The provided user session can't be null!");

    log.tracef("removeUserSession(%s, %s)%s", realm, session, getShortStackTrace());

    userSessionRepository.deleteUserSession(session.getId());
  }

  @Override
  public void removeUserSessions(RealmModel realm, UserModel user) {
    log.tracef("removeUserSessions(%s, %s)%s", realm, user, getShortStackTrace());

    userSessionRepository.findUserSessionsByUserId(user.getId()).forEach(userSessionRepository::deleteUserSession);
  }

  @Override
  public void removeAllExpired() {
    log.tracef("removeAllExpired()%s", getShortStackTrace());
    // NOOP: Handled by Cassandra-TTL
  }

  @Override
  public void removeExpired(RealmModel realm) {
    log.tracef("removeExpired(%s)%s", realm, getShortStackTrace());
    // NOOP: Handled by Cassandra-TTL
  }

  @Override
  public void removeUserSessions(RealmModel realm) {
    userSessionRepository.findAll().stream().forEach(userSessionRepository::deleteUserSession);
  }

  @Override
  public void onRealmRemoved(RealmModel realm) {
    log.tracef("onRealmRemoved(%s)%s", realm, getShortStackTrace());
    removeUserSessions(realm);
  }

  @Override
  public void onClientRemoved(RealmModel realm, ClientModel client) {
    log.tracef("onClientRemoved(%s, %s)%s", realm, client, getShortStackTrace());
    userSessionRepository.deleteClientSessions(client.getId());
  }

  @Override
  public UserSessionModel createOfflineUserSession(UserSessionModel userSession) {
    log.tracef("createOfflineUserSession(%s)%s", userSession, getShortStackTrace());

    UserSession offlineUserSession = createUserSessionEntityInstance(userSession, true);
    long currentTime = Time.currentTimeMillis();
    offlineUserSession.setTimestamp(currentTime);
    offlineUserSession.setLastSessionRefresh(currentTime);
    setUserSessionExpiration(offlineUserSession, userSession.getRealm());

    userSessionRepository.insertOrUpdate(offlineUserSession, userSession.getId());

    // set a reference for the offline user session to the original online user session
    UserSession userSessionEntity = userSessionRepository.findUserSessionById(userSession.getId());
    userSessionRepository.insertOrUpdate(userSessionEntity, offlineUserSession.getId());

    return entityToAdapterFunc(userSession.getRealm()).apply(offlineUserSession);
  }

  @Override
  public UserSessionModel getOfflineUserSession(RealmModel realm, String userSessionId) {
    log.tracef("getOfflineUserSession(%s, %s)%s", realm, userSessionId, getShortStackTrace());

    return getOfflineUserSessionEntityStream(realm, userSessionId)
        .findFirst()
        .map(entityToAdapterFunc(realm))
        .orElse(null);
  }

  @Override
  public void removeOfflineUserSession(RealmModel realm, UserSessionModel userSession) {
    Objects.requireNonNull(userSession, "The provided user session can't be null!");

    log.tracef("removeOfflineUserSession(%s, %s)%s", realm, userSession, getShortStackTrace());

    UserSession userSessionEntity = userSessionRepository.findUserSessionById(userSession.getId());
    if (userSessionEntity.isOffline()) {
      userSessionRepository.deleteUserSession(userSessionEntity);
    } else if (userSessionEntity.hasCorrespondingSession()) {
      userSessionRepository.deleteCorrespondingUserSession(userSessionEntity);
    }
  }

  @Override
  public AuthenticatedClientSessionModel createOfflineClientSession(AuthenticatedClientSessionModel clientSession, UserSessionModel offlineUserSession) {
    log.tracef("createOfflineClientSession(%s, %s)%s", clientSession, offlineUserSession, getShortStackTrace());

    AuthenticatedClientSession clientSessionEntity = createAuthenticatedClientSessionInstance(clientSession, offlineUserSession, true);
    int currentTime = Time.currentTime();
    clientSessionEntity.getNotes().put(AuthenticatedClientSessionModel.STARTED_AT_NOTE, String.valueOf(currentTime));
    clientSessionEntity.setTimestamp(Time.currentTimeMillis());
    RealmModel realm = clientSession.getRealm();
    setClientSessionExpiration(clientSessionEntity, realm, clientSession.getClient());

    Optional<UserSession> userSessionEntity = getOfflineUserSessionEntityStream(realm, offlineUserSession.getId()).findFirst();
    if (userSessionEntity.isPresent()) {
      UserSession userSession = userSessionEntity.get();
      String clientId = clientSession.getClient().getId();

      AuthenticatedClientSession existingClientSession = userSessionRepository.findClientSession(clientId, userSession.getId());
      if (existingClientSession != null) {
        userSessionRepository.deleteClientSession(existingClientSession);
      }

      userSessionRepository.insertOrUpdate(userSession.getId(), clientSessionEntity);

      UserSessionModel userSessionModel = entityToAdapterFunc(realm).apply(userSession);
      return userSessionModel == null ? null : userSessionModel.getAuthenticatedClientSessionByClient(clientId);
    }

    return null;
  }

  @Override
  public Stream<UserSessionModel> getOfflineUserSessionsStream(RealmModel realm, UserModel user) {
    log.tracef("getOfflineUserSessionsStream(%s, %s)%s", realm, user, getShortStackTrace());

    return userSessionRepository.findUserSessionsByUserId(user.getId()).stream()
        .filter(s -> s.getRealmId().equals(realm.getId()))
        .filter(UserSession::isOffline)
        .map(entityToAdapterFunc(realm));
  }

  @Override
  public UserSessionModel getOfflineUserSessionByBrokerSessionId(RealmModel realm, String brokerSessionId) {
    log.tracef("getOfflineUserSessionByBrokerSessionId(%s, %s)%s", realm, brokerSessionId, getShortStackTrace());

    return userSessionRepository.findUserSessionsByBrokerSession(brokerSessionId).stream()
        .filter(s -> s.getRealmId().equals(realm.getId()))
        .filter(UserSession::isOffline)
        .map(entityToAdapterFunc(realm))
        .findFirst()
        .orElse(null);
  }

  @Override
  public Stream<UserSessionModel> getOfflineUserSessionByBrokerUserIdStream(RealmModel realm, String brokerUserId) {
    log.tracef("getOfflineUserSessionByBrokerUserIdStream(%s, %s)%s", realm, brokerUserId, getShortStackTrace());

    return userSessionRepository.findUserSessionsByBrokerUserId(brokerUserId).stream()
        .filter(s -> s.getRealmId().equals(realm.getId()))
        .filter(UserSession::isOffline)
        .map(entityToAdapterFunc(realm));
  }

  @Override
  public long getOfflineSessionsCount(RealmModel realm, ClientModel client) {
    log.tracef("getOfflineSessionsCount(%s, %s)%s", realm, client, getShortStackTrace());

    // TODO: perf
    return userSessionRepository.findAll().stream()
        .filter(s -> s.getRealmId().equals(realm.getId()))
        .filter(UserSession::isOffline)
        .count();
  }

  @Override
  public Stream<UserSessionModel> getOfflineUserSessionsStream(RealmModel realm, ClientModel client, Integer firstResult, Integer maxResults) {
    log.tracef("getOfflineUserSessionsStream(%s, %s, %s, %s)%s", realm, client, firstResult, maxResults, getShortStackTrace());

    // TODO: perf
    return userSessionRepository.findAll().stream()
        .filter(s -> s.getRealmId().equals(realm.getId()))
        .filter(UserSession::isOffline)
        .skip(firstResult)
        .limit(maxResults)
        .sorted(Comparator.comparing(UserSession::getLastSessionRefresh))
        .map(entityToAdapterFunc(realm));
  }

  @Override
  public void importUserSessions(Collection<UserSessionModel> persistentUserSessions, boolean offline) {
    if (persistentUserSessions == null || persistentUserSessions.isEmpty()) {
      return;
    }

    persistentUserSessions.stream()
        .map(pus -> {
          UserSession userSessionEntity = createUserSessionEntityInstance(null, pus.getRealm().getId(),
              pus.getUser().getId(), pus.getLoginUsername(), pus.getIpAddress(), pus.getAuthMethod(),
              pus.isRememberMe(), pus.getBrokerSessionId(), pus.getBrokerUserId(), offline);

          for (Map.Entry<String, AuthenticatedClientSessionModel> entry : pus.getAuthenticatedClientSessions().entrySet()) {
            AuthenticatedClientSession clientSession = createAuthenticatedClientSessionInstance(entry.getValue(), entry.getValue().getUserSession(), offline);

            // Update timestamp to same value as userSession. LastSessionRefresh of userSession from DB will have correct value
            clientSession.setTimestamp(userSessionEntity.getLastSessionRefresh());

            userSessionRepository.insertOrUpdate(userSessionEntity.getId(), clientSession);
          }

          return userSessionEntity;
        })
        .forEach(userSessionRepository::insertOrUpdate);
  }

  @Override
  public int getStartupTime(RealmModel realm) {
    return realm.getNotBefore();
  }

  @Override
  public void close() {
    // NOOP
  }

  private Stream<UserSession> getOfflineUserSessionEntityStream(RealmModel realm, String userSessionId) {
    if (userSessionId == null) {
      return Stream.empty();
    }

    // first get a user entity by ID
    // check if it's an offline user session
    UserSession userSessionEntity = userSessionRepository.findUserSessionById(userSessionId);
    if (userSessionEntity != null) {
      if (Boolean.TRUE.equals(userSessionEntity.isOffline())) {
        return Stream.of(userSessionEntity);
      }
    } else {
      // no session found by the given ID, try to find by corresponding session ID
      return userSessionRepository.findUserSessionsByAttribute(CORRESPONDING_SESSION_ID, userSessionId).stream();
    }

    // it's online user session so lookup offline user session by corresponding session id reference
    String offlineUserSessionId = userSessionEntity.getNotes().get(CORRESPONDING_SESSION_ID);
    if (offlineUserSessionId != null) {
      return Stream.of(getUserSessionById(offlineUserSessionId));
    }

    return Stream.empty();
  }

  private UserSession getUserSessionById(String id) {
    if (id == null) return null;

    UserSession userSessionEntity = transientUserSessions.get(id);

    if (userSessionEntity == null) {
      return userSessionRepository.findUserSessionById(id);
    }
    return userSessionEntity;
  }

  private UserSession createUserSessionEntityInstance(UserSessionModel userSession, boolean offline) {
    UserSession entity = createUserSessionEntityInstance(null, userSession.getRealm().getId(), userSession.getUser().getId(),
        userSession.getLoginUsername(), userSession.getIpAddress(), userSession.getAuthMethod(), userSession.isRememberMe(),
        userSession.getBrokerSessionId(), userSession.getBrokerUserId(), offline);

    entity.setNotes(new ConcurrentHashMap<>(userSession.getNotes()));
    entity.setState(userSession.getState());
    entity.setTimestamp(TimeAdapter.fromSecondsToMilliseconds(userSession.getStarted()));
    entity.setLastSessionRefresh(TimeAdapter.fromSecondsToMilliseconds(userSession.getLastSessionRefresh()));

    return entity;
  }

  private UserSession createUserSessionEntityInstance(String id, String realmId, String userId, String loginUsername, String ipAddress,
                                                      String authMethod, boolean rememberMe, String brokerSessionId, String brokerUserId,
                                                      boolean offline) {
    long timestamp = Time.currentTimeMillis();

    return UserSession.builder()
        .id(id == null ? KeycloakModelUtils.generateId() : id)
        .realmId(realmId)
        .userId(userId)
        .loginUsername(loginUsername)
        .ipAddress(ipAddress)
        .authMethod(authMethod)
        .rememberMe(rememberMe)
        .brokerSessionId(brokerSessionId)
        .brokerUserId(brokerUserId)
        .offline(offline)
        .timestamp(timestamp)
        .lastSessionRefresh(timestamp)
        .notes(new ConcurrentHashMap<>())
        .build();
  }

  private AuthenticatedClientSession createAuthenticatedClientSessionEntityInstance(String id, String userSessionId, String realmId,
                                                                                    String clientId, boolean offline) {
    return AuthenticatedClientSession.builder()
        .realmId(realmId)
        .clientId(clientId)
        .userSessionId(userSessionId)
        .id(id == null ? KeycloakModelUtils.generateId() : id)
        .offline(offline)
        .timestamp(Time.currentTimeMillis())
        .notes(new ConcurrentHashMap<>())
        .build();
  }

  private AuthenticatedClientSession createAuthenticatedClientSessionInstance(AuthenticatedClientSessionModel clientSession,
                                                                              UserSessionModel userSession, boolean offline) {
    AuthenticatedClientSession entity = createAuthenticatedClientSessionEntityInstance(null, userSession.getId(),
        clientSession.getRealm().getId(), clientSession.getClient().getId(), offline);

    entity.setAction(clientSession.getAction());
    entity.setAuthMethod(clientSession.getProtocol());

    entity.setNotes(new ConcurrentHashMap<>(clientSession.getNotes()));
    entity.setRedirectUri(clientSession.getRedirectUri());
    entity.setTimestamp(TimeAdapter.fromSecondsToMilliseconds(clientSession.getTimestamp()));

    return entity;
  }
}
