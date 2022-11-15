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

import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSessionToAttributeMapping;
import org.keycloak.common.util.MultivaluedHashMap;

import java.util.List;
import java.util.Set;

public interface UserSessionRepository {
  void insertOrUpdate(UserSession session);
  void insertOrUpdate(UserSession session, String correspondingSessionId);

  UserSession findUserSessionById(String id);

  List<UserSession> findAll();

  List<UserSession> findUserSessionsByBrokerSession(String brokerSessionId);

  List<UserSession> findUserSessionsByUserId(String userId);

  List<UserSession> findUserSessionsByClientId(String clientId);

  List<UserSession> findUserSessionsByBrokerUserId(String brokerUserId);

  List<AuthenticatedClientSession> findClientSessionsByUserSessionId(String userSessionId);

  void deleteUserSession(UserSession session);

  void deleteUserSession(String id);

  void deleteCorrespondingUserSession(UserSession session);

  void insertOrUpdate(String userSessionId, AuthenticatedClientSession session);

  AuthenticatedClientSession findClientSession(String clientId, String userSessionId);

  void deleteClientSessions(String clientId);

  void deleteClientSession(AuthenticatedClientSession session);

  // Attributes
  Set<String> findUserSessionIdsByAttribute(String name, String value, int firstResult, int maxResult);

  List<UserSession> findUserSessionsByAttribute(String name, String value);

  UserSession findUserSessionByAttribute(String name, String value);

  MultivaluedHashMap<String, String> findAllUserSessionAttributes(String userSessionId);

  UserSessionToAttributeMapping findUserSessionAttribute(String userSessionId, String attributeName);

  void insertOrUpdate(UserSessionToAttributeMapping mapping);

  boolean deleteUserSessionAttribute(String userSessionId, String attributeName);
}
