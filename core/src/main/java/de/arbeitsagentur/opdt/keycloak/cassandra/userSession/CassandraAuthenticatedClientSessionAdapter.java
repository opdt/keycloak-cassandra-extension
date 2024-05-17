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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraJsonSerialization;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSessionValue;
import de.arbeitsagentur.opdt.keycloak.common.TimeAdapter;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.OAuth2Constants;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;
import org.keycloak.models.utils.SessionExpirationUtils;

@JBossLog
@EqualsAndHashCode(of = "userSession")
@AllArgsConstructor
public abstract class CassandraAuthenticatedClientSessionAdapter
    implements AuthenticatedClientSessionModel {
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
  public String getCurrentRefreshToken() {
    if (realm.getRefreshTokenMaxReuse() > 0
        || !session
            .getContext()
            .getHttpRequest()
            .getDecodedFormParameters()
            .containsKey(OAuth2Constants.REFRESH_TOKEN)) {
      log.debug(
          "RefreshTokenMaxReuse > 0 / no refresh_token in form-params -> use standard behavior for refresh-token-rotation");
      return clientSessionEntity.getCurrentRefreshToken();
    } else {
      String encodedRefreshToken =
          session
              .getContext()
              .getHttpRequest()
              .getDecodedFormParameters()
              .getFirst(OAuth2Constants.REFRESH_TOKEN);
      String tokenBodyEncoded = encodedRefreshToken.split("\\.")[1];
      String tokenBody = new String(Base64.getDecoder().decode(tokenBodyEncoded));

      try {
        RefreshTokenRepresentation refreshTokenRepresentation =
            CassandraJsonSerialization.readValue(tokenBody, new TypeReference<>() {});
        return refreshTokenRepresentation.getJti();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void setCurrentRefreshToken(String currentRefreshToken) {
    clientSessionEntity.setCurrentRefreshToken(currentRefreshToken); // for fallback use
  }

  @Override
  public int getCurrentRefreshTokenUseCount() {
    if (realm.getRefreshTokenMaxReuse() > 0
        || !session
            .getContext()
            .getHttpRequest()
            .getDecodedFormParameters()
            .containsKey(OAuth2Constants.REFRESH_TOKEN)) {
      log.debug(
          "RefreshTokenMaxReuse > 0 / no refresh_token in form-params -> use standard behavior for refresh-token-rotation");
      Integer currentRefreshTokenUseCount = clientSessionEntity.getCurrentRefreshTokenUseCount();
      return currentRefreshTokenUseCount != null ? currentRefreshTokenUseCount : 0;
    } else {

      Long lastUse = clientSessionEntity.getRefreshTokenUses().get(getCurrentRefreshToken());
      if (lastUse == null
          || lastUse
              > Time.currentTimeMillis() - realm.getAttribute("refreshTokenReuseInterval", 0L)) {
        return 0; // do not count refresh
      }
      return 1;
    }
  }

  @Override
  public void setCurrentRefreshTokenUseCount(int currentRefreshTokenUseCount) {
    if (realm.getRefreshTokenMaxReuse() > 0
        || !session
            .getContext()
            .getHttpRequest()
            .getDecodedFormParameters()
            .containsKey(OAuth2Constants.REFRESH_TOKEN)) {
      clientSessionEntity.setCurrentRefreshTokenUseCount(currentRefreshTokenUseCount);
      userSession.markAsUpdated();
    } else {
      String currentRefreshToken = getCurrentRefreshToken();

      // We only know two states -> first use sets "lastUse", all other later uses dont have to
      // change anything
      if (currentRefreshTokenUseCount > 0
          && !clientSessionEntity.getRefreshTokenUses().containsKey(currentRefreshToken)) {
        clientSessionEntity
            .getRefreshTokenUses()
            .put(currentRefreshToken, Time.currentTimeMillis());
        userSession.markAsUpdated();
      }

      // Clean up too old use trackers for tokens which are already expired
      long idleTimestamp =
          SessionExpirationUtils.calculateClientSessionIdleTimestamp(
              userSession.isOffline(),
              isUserSessionRememberMe(),
              TimeUnit.SECONDS.toMillis(getTimestamp()),
              realm,
              getClient());
      long effectiveIdleTime = idleTimestamp - Time.currentTimeMillis();
      Set<Map.Entry<String, Long>> idsToRemove =
          clientSessionEntity.getRefreshTokenUses().entrySet().stream()
              .filter(e -> e.getValue() < Time.currentTimeMillis() - effectiveIdleTime)
              .collect(Collectors.toSet());
      idsToRemove.forEach(
          e -> {
            clientSessionEntity.getRefreshTokenUses().remove(e.getKey());
            userSession.markAsUpdated();
          });
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

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class RefreshTokenRepresentation {
    private String jti;
  }
}
