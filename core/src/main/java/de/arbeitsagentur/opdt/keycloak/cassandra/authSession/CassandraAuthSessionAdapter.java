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

import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.AuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSession;
import lombok.RequiredArgsConstructor;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import java.util.*;

@RequiredArgsConstructor
public class CassandraAuthSessionAdapter implements AuthenticationSessionModel {
  private final KeycloakSession session;
  private final RealmModel realm;
  private final RootAuthenticationSessionModel parentSession;
  private final AuthenticationSession authenticationSession;
  private final AuthSessionRepository authSessionRepository;

  @Override
  public String getTabId() {
    return authenticationSession.getTabId();
  }

  @Override
  public RootAuthenticationSessionModel getParentSession() {
    return parentSession;
  }

  @Override
  public Map<String, ExecutionStatus> getExecutionStatus() {
    return authenticationSession.getExecutionStatus();
  }

  @Override
  public void setExecutionStatus(String authenticator, ExecutionStatus status) {
    Objects.requireNonNull(authenticator, "The provided authenticator can't be null!");
    Objects.requireNonNull(status, "The provided execution status can't be null!");
    authenticationSession.getExecutionStatus().put(authenticator, status);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public void clearExecutionStatus() {
    authenticationSession.getExecutionStatus().clear();
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public UserModel getAuthenticatedUser() {
    return authenticationSession.getUserId() == null ? null : session.users().getUserById(getRealm(), authenticationSession.getUserId());
  }

  @Override
  public void setAuthenticatedUser(UserModel user) {
    String userId = (user == null) ? null : user.getId();
    authenticationSession.setUserId(userId);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public Set<String> getRequiredActions() {
    return new HashSet<>(authenticationSession.getRequiredActions());
  }

  @Override
  public void addRequiredAction(String action) {
    Objects.requireNonNull(action, "The provided action can't be null!");
    authenticationSession.getRequiredActions().add(action);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public void removeRequiredAction(String action) {
    Objects.requireNonNull(action, "The provided action can't be null!");
    authenticationSession.getRequiredActions().remove(action);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public void addRequiredAction(UserModel.RequiredAction action) {
    Objects.requireNonNull(action, "The provided action can't be null!");
    authenticationSession.getRequiredActions().add(action.name());
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public void removeRequiredAction(UserModel.RequiredAction action) {
    Objects.requireNonNull(action, "The provided action can't be null!");
    authenticationSession.getRequiredActions().remove(action.name());
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public void setUserSessionNote(String name, String value) {
    if(value == null) {
      authenticationSession.getUserNotes().remove(name);
    } else {
      authenticationSession.getUserNotes().put(name, value);
    }

    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public Map<String, String> getUserSessionNotes() {
    return authenticationSession.getUserNotes();
  }

  @Override
  public void clearUserSessionNotes() {
    authenticationSession.getUserNotes().clear();
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public String getAuthNote(String name) {
    return authenticationSession.getAuthNotes().get(name);
  }

  @Override
  public void setAuthNote(String name, String value) {
    if(value == null) {
      removeAuthNote(name);
      return;
    }

    authenticationSession.getAuthNotes().put(name, value);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public void removeAuthNote(String name) {
    authenticationSession.getAuthNotes().remove(name);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public void clearAuthNotes() {
    authenticationSession.getAuthNotes().clear();
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public String getClientNote(String name) {
    return authenticationSession.getClientNotes().get(name);
  }

  @Override
  public void setClientNote(String name, String value) {
    if(value == null) {
      removeClientNote(name);
      return;
    }

    authenticationSession.getClientNotes().put(name, value);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public void removeClientNote(String name) {
    authenticationSession.getClientNotes().remove(name);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public Map<String, String> getClientNotes() {
    return authenticationSession.getClientNotes();
  }

  @Override
  public void clearClientNotes() {
    authenticationSession.getClientNotes().clear();
  }

  @Override
  public Set<String> getClientScopes() {
    return authenticationSession.getClientScopes();
  }

  @Override
  public void setClientScopes(Set<String> clientScopes) {
    Objects.requireNonNull(clientScopes, "The provided client scopes set can't be null!");
    authenticationSession.setClientScopes(clientScopes);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public String getRedirectUri() {
    return authenticationSession.getRedirectUri();
  }

  @Override
  public void setRedirectUri(String uri) {
    authenticationSession.setRedirectUri(uri);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public RealmModel getRealm() {
    return realm;
  }

  @Override
  public ClientModel getClient() {
    return realm.getClientById(authenticationSession.getClientId());
  }

  @Override
  public String getAction() {
    return authenticationSession.getAction();
  }

  @Override
  public void setAction(String action) {
    authenticationSession.setAction(action);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }

  @Override
  public String getProtocol() {
    return authenticationSession.getProtocol();
  }

  @Override
  public void setProtocol(String method) {
    authenticationSession.setProtocol(method);
    authSessionRepository.insertOrUpdate(authenticationSession);
  }
}
