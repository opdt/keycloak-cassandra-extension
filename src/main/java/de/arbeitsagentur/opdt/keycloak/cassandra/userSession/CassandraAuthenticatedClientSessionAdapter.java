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
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.keycloak.models.*;
import org.keycloak.models.map.common.TimeAdapter;

import java.util.Map;

import static de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraSessionExpiration.setClientSessionExpiration;

@EqualsAndHashCode(of = "userSessionEntity")
@AllArgsConstructor
public abstract class CassandraAuthenticatedClientSessionAdapter implements AuthenticatedClientSessionModel {
  protected KeycloakSession session;
  protected RealmModel realm;
  protected UserSessionModel userSession;
  protected AuthenticatedClientSession clientSessionEntity;
  protected UserSessionRepository userSessionRepository;

  @Override
  public String getId() {
    return clientSessionEntity.getId();
  }

  @Override
  public int getTimestamp() {
    Long timestamp = clientSessionEntity.getTimestamp();
    return timestamp != null ? TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(TimeAdapter.fromMilliSecondsToSeconds(timestamp)) : 0;
  }

  @Override
  public void setTimestamp(int timestamp) {
    clientSessionEntity.setTimestamp(TimeAdapter.fromSecondsToMilliseconds(timestamp));

    // whenever the timestamp is changed recompute the expiration time
    setClientSessionExpiration(clientSessionEntity, realm, getClient());
    userSessionRepository.insertOrUpdate(userSession.getId(), clientSessionEntity);
  }

  @Override
  public UserSessionModel getUserSession() {
    return userSession;
  }

  @Override
  public String getCurrentRefreshToken() {
    return clientSessionEntity.getCurrentRefreshToken();
  }

  @Override
  public void setCurrentRefreshToken(String currentRefreshToken) {
    clientSessionEntity.setCurrentRefreshToken(currentRefreshToken);
    userSessionRepository.insertOrUpdate(userSession.getId(), clientSessionEntity);
  }

  @Override
  public int getCurrentRefreshTokenUseCount() {
    Integer currentRefreshTokenUseCount = clientSessionEntity.getCurrentRefreshTokenUseCount();
    return currentRefreshTokenUseCount != null ? currentRefreshTokenUseCount : 0;
  }

  @Override
  public void setCurrentRefreshTokenUseCount(int currentRefreshTokenUseCount) {
    clientSessionEntity.setCurrentRefreshTokenUseCount(currentRefreshTokenUseCount);
    userSessionRepository.insertOrUpdate(userSession.getId(), clientSessionEntity);
  }

  @Override
  public String getNote(String name) {
    return clientSessionEntity.getNotes().get(name);
  }

  @Override
  public void setNote(String name, String value) {
    if(value == null) {
      removeNote(name);
      return;
    }

    clientSessionEntity.getNotes().put(name, value);
    userSessionRepository.insertOrUpdate(userSession.getId(), clientSessionEntity);
  }

  @Override
  public void removeNote(String name) {
    clientSessionEntity.getNotes().remove(name);
    userSessionRepository.insertOrUpdate(userSession.getId(), clientSessionEntity);
  }

  @Override
  public Map<String, String> getNotes() {
    return clientSessionEntity.getNotes();
  }

  @Override
  public String getRedirectUri() {
    return clientSessionEntity.getRedirectUri();
  }

  @Override
  public void setRedirectUri(String uri) {
    clientSessionEntity.setRedirectUri(uri);
    userSessionRepository.insertOrUpdate(userSession.getId(), clientSessionEntity);
  }

  @Override
  public RealmModel getRealm() {
    return realm;
  }

  @Override
  public ClientModel getClient() {
    return realm.getClientById(clientSessionEntity.getClientId());
  }

  @Override
  public String getAction() {
    return clientSessionEntity.getAction();
  }

  @Override
  public void setAction(String action) {
    clientSessionEntity.setAction(action);
    userSessionRepository.insertOrUpdate(userSession.getId(), clientSessionEntity);
  }

  @Override
  public String getProtocol() {
    return clientSessionEntity.getAuthMethod();
  }

  @Override
  public void setProtocol(String method) {
    clientSessionEntity.setAuthMethod(method);
    userSessionRepository.insertOrUpdate(userSession.getId(), clientSessionEntity);
  }
}
