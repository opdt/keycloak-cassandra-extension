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

import de.arbeitsagentur.opdt.keycloak.cassandra.StreamExtensions;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AttributeToUserSessionMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSessionToAttributeMapping;
import lombok.RequiredArgsConstructor;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.common.util.Time;
import org.keycloak.models.map.common.TimeAdapter;

import java.util.*;
import java.util.stream.Collectors;

import static org.keycloak.models.UserSessionModel.CORRESPONDING_SESSION_ID;

@RequiredArgsConstructor
public class CassandraUserSessionRepository implements UserSessionRepository {
  private static final String CLIENT_IDS = "clientIds";
  private static final String USER_ID = "userId";
  private static final String BROKER_USER_ID = "brokerUserId";
  private static final String BROKER_SESSION_ID = "brokerSessionId";
  private final UserSessionDao dao;

  @Override
  public void insertOrUpdate(UserSession session) {
    insertOrUpdate(session, null);
  }

  @Override
  public void insertOrUpdate(UserSession session, String correspondingSessionId) {
    if (correspondingSessionId != null) {
      insertOrUpdate(new UserSessionToAttributeMapping(session.getId(), CORRESPONDING_SESSION_ID, Arrays.asList(correspondingSessionId)));
      session.getNotes().put(CORRESPONDING_SESSION_ID, correspondingSessionId); // compatibility
    }

    if (session.getExpiration() == null) {
      dao.insertOrUpdate(session);
    } else {
      int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(TimeAdapter.fromMilliSecondsToSeconds(session.getExpiration() - Time.currentTimeMillis()));
      dao.insertOrUpdate(session, ttl);
    }

    if (session.getUserId() != null) {
      insertOrUpdate(new UserSessionToAttributeMapping(session.getId(), USER_ID, Arrays.asList(session.getUserId())));
    }

    if (session.getBrokerUserId() != null) {
      insertOrUpdate(new UserSessionToAttributeMapping(session.getId(), BROKER_USER_ID, Arrays.asList(session.getBrokerUserId())));
    }

    if (session.getBrokerSessionId() != null) {
      insertOrUpdate(new UserSessionToAttributeMapping(session.getId(), BROKER_SESSION_ID, Arrays.asList(session.getBrokerSessionId())));
    }

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
  public List<AuthenticatedClientSession> findClientSessionsByUserSessionId(String userSessionId) {
    UserSessionToAttributeMapping clientIds = findUserSessionAttribute(userSessionId, CLIENT_IDS);
    if (clientIds == null) {
      return Collections.emptyList();
    }

    return clientIds.getAttributeValues().stream()
        .map(clientId -> findClientSession(clientId, userSessionId))
        .collect(Collectors.toList());
  }

  @Override
  public void deleteUserSession(UserSession session) {
    deleteUserSession(session.getId());
  }

  @Override
  public void deleteUserSession(String id) {
    dao.deleteUserSession(id);

    // Client Sessions
    UserSessionToAttributeMapping clientIdsAttribute = findUserSessionAttribute(CLIENT_IDS, id);
    if (clientIdsAttribute != null) {
      clientIdsAttribute.getAttributeValues().forEach(this::deleteClientSessions);
    }

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
    UserSessionToAttributeMapping correspondingIdAttribute = findUserSessionAttribute(session.getId(), CORRESPONDING_SESSION_ID);
    if (correspondingIdAttribute == null) {
      return;
    }

    deleteUserSession(correspondingIdAttribute.getUserSessionId());
    session.getNotes().remove(CORRESPONDING_SESSION_ID);
    insertOrUpdate(session);
    deleteUserSessionAttribute(session.getId(), CORRESPONDING_SESSION_ID);
  }

  // AuthenticatedClientSessions

  @Override
  public void insertOrUpdate(String userSessionId, AuthenticatedClientSession session) {
    int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(TimeAdapter.fromMilliSecondsToSeconds(session.getExpiration() - Time.currentTimeMillis()));

    dao.insertOrUpdate(session, ttl);
    UserSessionToAttributeMapping attribute = dao.findAttribute(userSessionId, CLIENT_IDS);

    if (attribute == null) {
      attribute = new UserSessionToAttributeMapping(userSessionId, CLIENT_IDS, Arrays.asList(session.getClientId()));
    } else if (!attribute.getAttributeValues().contains(session.getClientId())){
      attribute.getAttributeValues().add(session.getClientId());
    }

    insertOrUpdate(attribute);
  }

  @Override
  public AuthenticatedClientSession findClientSession(String clientId, String userSessionId) {
    return dao.findClientSession(clientId, userSessionId);
  }

  @Override
  public void deleteClientSessions(String clientId) {
    dao.deleteClientSessions(clientId);
  }

  @Override
  public void deleteClientSession(AuthenticatedClientSession session) {
    dao.deleteClientSession(session);
  }

  // Attributes
  @Override
  public Set<String> findUserSessionIdsByAttribute(String name, String value, int firstResult, int maxResult) {
    return StreamExtensions.paginated(dao.findByAttribute(name, value), firstResult, maxResult)
        .map(AttributeToUserSessionMapping::getUserSessionId)
        .collect(Collectors.toSet());
  }

  @Override
  public List<UserSession> findUserSessionsByAttribute(String name, String value) {
    return dao.findByAttribute(name, value).all().stream()
        .map(AttributeToUserSessionMapping::getUserSessionId)
        .flatMap(id -> Optional.ofNullable(findUserSessionById(id)).stream())
        .collect(Collectors.toList());
  }

  @Override
  public UserSession findUserSessionByAttribute(String name, String value) {
    List<UserSession> userSessions = findUserSessionsByAttribute(name, value);

    if (userSessions.size() > 1) {
      throw new IllegalStateException("Found more than one userSession with attributeName " + name + " and value " + value);
    }

    if (userSessions.isEmpty()) {
      return null;
    }

    return userSessions.get(0);
  }

  @Override
  public MultivaluedHashMap<String, String> findAllUserSessionAttributes(String userSessionId) {
    List<UserSessionToAttributeMapping> attributeMappings = dao.findAllAttributes(userSessionId).all();
    MultivaluedHashMap<String, String> result = new MultivaluedHashMap<>();

    attributeMappings.forEach(mapping -> result.addAll(mapping.getAttributeName(), mapping.getAttributeValues()));

    return result;
  }

  @Override
  public UserSessionToAttributeMapping findUserSessionAttribute(String userSessionId, String attributeName) {
    return dao.findAttribute(userSessionId, attributeName);
  }

  @Override
  public void insertOrUpdate(UserSessionToAttributeMapping mapping) {
    UserSessionToAttributeMapping oldAttribute = dao.findAttribute(mapping.getUserSessionId(), mapping.getAttributeName());
    dao.insert(mapping);

    if (oldAttribute != null) {
      // Alte AttributeToUserSessionMappings löschen, da die Values als Teil des PartitionKey nicht
      // geändert werden können
      oldAttribute
          .getAttributeValues()
          .forEach(value -> dao.deleteAttributeToUserSessionMapping(oldAttribute.getAttributeName(), value, oldAttribute.getUserSessionId()));
    }

    mapping
        .getAttributeValues()
        .forEach(
            value -> {
              AttributeToUserSessionMapping attributeToUserSessionMapping =
                  new AttributeToUserSessionMapping(
                      mapping.getAttributeName(),
                      value,
                      mapping.getUserSessionId());
              dao.insert(attributeToUserSessionMapping);
            });
  }

  @Override
  public boolean deleteUserSessionAttribute(String userSessionId, String attributeName) {
    UserSessionToAttributeMapping attribute = findUserSessionAttribute(userSessionId, attributeName);

    if (attribute == null) {
      return false;
    }

    // Beide Mapping-Tabellen beachten!
    dao.deleteAttribute(userSessionId, attributeName);
    attribute
        .getAttributeValues()
        .forEach(value -> dao.deleteAttributeToUserSessionMapping(attributeName, value, userSessionId));
    return true;
  }

}
