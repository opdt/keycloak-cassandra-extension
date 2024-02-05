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
package de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence;

import static org.keycloak.models.UserSessionModel.CORRESPONDING_SESSION_ID;
import static org.keycloak.models.UserSessionModel.SessionPersistenceState.PERSISTENT;

import de.arbeitsagentur.opdt.keycloak.cassandra.StreamExtensions;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AttributeToUserSessionMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSessionValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSessionToAttributeMapping;
import de.arbeitsagentur.opdt.keycloak.mapstorage.common.TimeAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.common.util.Time;

@RequiredArgsConstructor
public class CassandraUserSessionRepository implements UserSessionRepository {
  private static final String CLIENT_IDS = "clientIds";
  private static final String USER_ID = "userId";
  private static final String BROKER_USER_ID = "brokerUserId";
  private static final String BROKER_SESSION_ID = "brokerSessionId";
  private final UserSessionDao dao;

  @Override
  public void insert(UserSession session) {
    insert(session, null);
  }

  @Override
  public void update(UserSession session) {
    update(session, null);
  }

  @Override
  public void insert(UserSession session, String correspondingSessionId) {
    if (correspondingSessionId != null) {
      insertOrUpdate(
          session,
          new UserSessionToAttributeMapping(
              session.getId(), CORRESPONDING_SESSION_ID, Arrays.asList(correspondingSessionId)));
      session.getNotes().put(CORRESPONDING_SESSION_ID, correspondingSessionId); // compatibility
    }

    insertOrUpdate(session);

    if (session.getUserId() != null) {
      insertOrUpdate(
          session,
          new UserSessionToAttributeMapping(
              session.getId(), USER_ID, Arrays.asList(session.getUserId())));
    }

    if (session.getBrokerUserId() != null) {
      insertOrUpdate(
          session,
          new UserSessionToAttributeMapping(
              session.getId(), BROKER_USER_ID, Arrays.asList(session.getBrokerUserId())));
    }

    if (session.getBrokerSessionId() != null) {
      insertOrUpdate(
          session,
          new UserSessionToAttributeMapping(
              session.getId(), BROKER_SESSION_ID, Arrays.asList(session.getBrokerSessionId())));
    }
  }

  @Override
  public void update(UserSession session, String correspondingSessionId) {
    if (correspondingSessionId != null) {
      insertOrUpdate(
          session,
          new UserSessionToAttributeMapping(
              session.getId(), CORRESPONDING_SESSION_ID, Arrays.asList(correspondingSessionId)));
      session.getNotes().put(CORRESPONDING_SESSION_ID, correspondingSessionId); // compatibility
    }

    insertOrUpdate(session);
  }

  @Override
  public void addClientSession(UserSession session, AuthenticatedClientSessionValue clientSession) {
    session.getClientSessions().put(clientSession.getClientId(), clientSession);

    UserSessionToAttributeMapping clientIdsAttribute =
        new UserSessionToAttributeMapping(session.getId(), CLIENT_IDS, new ArrayList<>());

    for (String clientId : session.getClientSessions().keySet()) {
      clientIdsAttribute.getAttributeValues().add(clientId);
    }

    if (!clientIdsAttribute.getAttributeValues().isEmpty()) {
      insertOrUpdate(session, clientIdsAttribute);
    }

    insertOrUpdate(session);
  }

  @Override
  public UserSession findUserSessionById(String id) {
    return dao.findById(id);
  }

  @Override
  public List<UserSession> findAll() {
    return dao.findAll().all();
  }

  @Override
  public List<UserSession> findUserSessionsByBrokerSession(String brokerSessionId) {
    return findUserSessionsByAttribute(BROKER_SESSION_ID, brokerSessionId);
  }

  @Override
  public List<UserSession> findUserSessionsByUserId(String userId) {
    return findUserSessionsByAttribute(USER_ID, userId);
  }

  @Override
  public List<UserSession> findUserSessionsByClientId(String clientId) {
    return findUserSessionsByAttribute(CLIENT_IDS, clientId);
  }

  @Override
  public List<UserSession> findUserSessionsByBrokerUserId(String brokerUserId) {
    return findUserSessionsByAttribute(BROKER_USER_ID, brokerUserId);
  }

  @Override
  public void deleteUserSession(UserSession session) {
    deleteUserSession(session.getId());
  }

  @Override
  public void deleteUserSession(String id) {
    dao.deleteUserSession(id);

    // Attributes
    for (UserSessionToAttributeMapping attribute : dao.findAllAttributes(id)) {
      for (String attributeValue : attribute.getAttributeValues()) {
        dao.deleteAttributeToUserSessionMapping(attribute.getAttributeName(), attributeValue, id);
      }
    }

    dao.deleteAllUserSessionToAttributeMappings(id);
  }

  @Override
  public void deleteCorrespondingUserSession(UserSession session) {
    if (!session.getNotes().containsKey(CORRESPONDING_SESSION_ID)) {
      return;
    }

    deleteUserSession(session.getNotes().get(CORRESPONDING_SESSION_ID));
  }

  // Attributes
  @Override
  public Set<String> findUserSessionIdsByAttribute(
      String name, String value, int firstResult, int maxResult) {
    return StreamExtensions.paginated(dao.findByAttribute(name, value), firstResult, maxResult)
        .map(AttributeToUserSessionMapping::getUserSessionId)
        .collect(Collectors.toSet());
  }

  @Override
  public List<UserSession> findUserSessionsByAttribute(String name, String value) {
    List<String> userIds =
        dao.findByAttribute(name, value).all().stream()
            .map(AttributeToUserSessionMapping::getUserSessionId)
            .collect(Collectors.toList());

    return dao.findByIds(userIds).all();
  }

  @Override
  public UserSession findUserSessionByAttribute(String name, String value) {
    List<UserSession> userSessions = findUserSessionsByAttribute(name, value);

    if (userSessions.size() > 1) {
      throw new IllegalStateException(
          "Found more than one userSession with attributeName " + name + " and value " + value);
    }

    if (userSessions.isEmpty()) {
      return null;
    }

    return userSessions.get(0);
  }

  @Override
  public MultivaluedHashMap<String, String> findAllUserSessionAttributes(String userSessionId) {
    List<UserSessionToAttributeMapping> attributeMappings =
        dao.findAllAttributes(userSessionId).all();
    MultivaluedHashMap<String, String> result = new MultivaluedHashMap<>();

    attributeMappings.forEach(
        mapping -> result.addAll(mapping.getAttributeName(), mapping.getAttributeValues()));

    return result;
  }

  @Override
  public UserSessionToAttributeMapping findUserSessionAttribute(
      String userSessionId, String attributeName) {
    return dao.findAttribute(userSessionId, attributeName);
  }

  private void insertOrUpdate(UserSession session) {
    if ((session.getOffline() != null && session.getOffline())
        || PERSISTENT.equals(session.getPersistenceState())) {
      if (session.getExpiration() == null) {
        dao.insertOrUpdate(session);
      } else {
        int ttl =
            TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(
                TimeAdapter.fromMilliSecondsToSeconds(
                    session.getExpiration() - Time.currentTimeMillis()));
        dao.insertOrUpdate(session, ttl);
      }
    }
  }

  private void insertOrUpdate(UserSession session, UserSessionToAttributeMapping mapping) {
    Integer ttl =
        session.getExpiration() == null
            ? null
            : TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(
                TimeAdapter.fromMilliSecondsToSeconds(
                    session.getExpiration() - Time.currentTimeMillis()));
    UserSessionToAttributeMapping oldAttribute =
        dao.findAttribute(mapping.getUserSessionId(), mapping.getAttributeName());

    if (ttl == null) {
      dao.insertOrUpdate(mapping);
    } else {
      dao.insertOrUpdate(mapping, ttl);
    }

    if (oldAttribute != null) {
      // Alte AttributeToUserSessionMappings löschen, da die Values als Teil des PartitionKey nicht
      // geändert werden können
      oldAttribute
          .getAttributeValues()
          .forEach(
              value ->
                  dao.deleteAttributeToUserSessionMapping(
                      oldAttribute.getAttributeName(), value, oldAttribute.getUserSessionId()));
    }

    mapping
        .getAttributeValues()
        .forEach(
            value -> {
              AttributeToUserSessionMapping attributeToUserSessionMapping =
                  new AttributeToUserSessionMapping(
                      mapping.getAttributeName(), value, mapping.getUserSessionId());

              if (ttl == null) {
                dao.insert(attributeToUserSessionMapping);
              } else {
                dao.insert(attributeToUserSessionMapping, ttl);
              }
            });
  }
}
