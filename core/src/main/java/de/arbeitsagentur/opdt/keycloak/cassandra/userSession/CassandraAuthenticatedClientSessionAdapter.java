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

import static de.arbeitsagentur.opdt.keycloak.cassandra.userSession.expiration.CassandraSessionExpiration.setClientSessionExpiration;

import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSessionValue;
import de.arbeitsagentur.opdt.keycloak.common.TimeAdapter;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;

@JBossLog
@EqualsAndHashCode(of = "userSession")
@AllArgsConstructor
public abstract class CassandraAuthenticatedClientSessionAdapter
    implements AuthenticatedClientSessionModel {
  private static final String REFRESH_TOKEN_LAST_USE_PREFIX = "refreshTokenLastUsePrefix";

  protected KeycloakSession session;
  protected RealmModel realm;
  protected CassandraUserSessionAdapter userSession;
  protected AuthenticatedClientSessionValue clientSessionEntity;

  public AuthenticatedClientSessionValue getClientSessionEntity() {
    return clientSessionEntity;
  }

  @Override
  public String getId() {
    return clientSessionEntity.getId();
  }

  @Override
  public int getTimestamp() {
    Long timestamp = clientSessionEntity.getTimestamp();
    return timestamp != null
        ? TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(
            TimeAdapter.fromMilliSecondsToSeconds(timestamp))
        : 0;
  }

  @Override
  public void setTimestamp(int timestamp) {
    clientSessionEntity.setTimestamp(TimeAdapter.fromSecondsToMilliseconds(timestamp));

    // whenever the timestamp is changed recompute the expiration time
    setClientSessionExpiration(
        clientSessionEntity, userSession.getSessionExpirationData(), getClient());
    userSession.markAsUpdated();
  }

  @Override
  public UserSessionModel getUserSession() {
    return userSession;
  }

  @Override
  public int getRefreshTokenUseCount(String reuseId) {
    String currentCount = getNote(REFRESH_TOKEN_USE_PREFIX + reuseId);

    if (currentCount == null) {
      return 0;
    }

    String lastUseTimestampString = getNote(REFRESH_TOKEN_LAST_USE_PREFIX + reuseId);
    if (lastUseTimestampString == null) {
      return Integer.parseInt(currentCount);
    }

    long lastUseTimestamp = Long.parseLong(lastUseTimestampString);
    if (lastUseTimestamp
        > Time.currentTimeMillis() - realm.getAttribute("refreshTokenReuseInterval", 0L)) {
      return Math.max(0, Integer.parseInt(currentCount) - 1); // do not count refresh
    }

    return Integer.parseInt(currentCount);
  }

  @Override
  public void setRefreshTokenUseCount(String reuseId, int count) {
    String currentCountStr = getNote(REFRESH_TOKEN_USE_PREFIX + reuseId);
    int currentCount =
        currentCountStr == null || currentCountStr.isEmpty()
            ? 0
            : Integer.parseInt(currentCountStr);

    if (count != currentCount) {
      setNote(REFRESH_TOKEN_LAST_USE_PREFIX + reuseId, String.valueOf(Time.currentTimeMillis()));
      setNote(REFRESH_TOKEN_USE_PREFIX + reuseId, String.valueOf(count));
    }
  }

  @Override
  public String getNote(String name) {
    return clientSessionEntity.getNotes().get(name);
  }

  @Override
  public void setNote(String name, String value) {
    if (value == null) {
      removeNote(name);
      return;
    }

    if (!clientSessionEntity.getNotes().containsKey(name)
        || !clientSessionEntity.getNotes().get(name).equals(value)) {
      clientSessionEntity.getNotes().put(name, value);

      userSession.markAsUpdated();
    }
  }

  @Override
  public void removeNote(String name) {
    if (clientSessionEntity.getNotes().containsKey(name)) {
      clientSessionEntity.getNotes().remove(name);
      userSession.markAsUpdated();
    }
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
    if (clientSessionEntity.getRedirectUri() == null
        || !clientSessionEntity.getRedirectUri().equals(uri)) {
      clientSessionEntity.setRedirectUri(uri);
      userSession.markAsUpdated();
    }
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
    if (clientSessionEntity.getAction() == null
        || !clientSessionEntity.getAction().equals(action)) {
      clientSessionEntity.setAction(action);
      userSession.markAsUpdated();
    }
  }

  @Override
  public String getProtocol() {
    return clientSessionEntity.getAuthMethod();
  }

  @Override
  public void setProtocol(String method) {
    if (clientSessionEntity.getAuthMethod() == null
        || !clientSessionEntity.getAuthMethod().equals(method)) {
      clientSessionEntity.setAuthMethod(method);
      userSession.markAsUpdated();
    }
  }
}
