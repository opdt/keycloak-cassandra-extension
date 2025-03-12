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

import static org.keycloak.models.Constants.SESSION_NOTE_LIGHTWEIGHT_USER;
import static org.keycloak.models.light.LightweightUserAdapter.isLightweightUser;

import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.AuthenticationSession;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.keycloak.common.Profile;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.light.LightweightUserAdapter;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

@RequiredArgsConstructor
public class CassandraAuthSessionAdapter implements AuthenticationSessionModel {
    private final KeycloakSession session;
    private final RealmModel realm;
    private final CassandraRootAuthSessionAdapter parentSession;
    private final AuthenticationSession authenticationSession;
    private final AuthSessionRepository authSessionRepository;

    private boolean updated = false;
    private boolean deleted = false;

    public void markDeleted() {
        deleted = true;
    }

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
        updated = true;
    }

    @Override
    public void clearExecutionStatus() {
        authenticationSession.getExecutionStatus().clear();
        updated = true;
    }

    @Override
    public UserModel getAuthenticatedUser() {
        if (Profile.isFeatureEnabled(Profile.Feature.TRANSIENT_USERS)
                && getUserSessionNotes().containsKey(SESSION_NOTE_LIGHTWEIGHT_USER)) {
            LightweightUserAdapter cachedUser = session.getAttribute(
                    "authSession.user." + getParentSession().getId(), LightweightUserAdapter.class);

            if (cachedUser != null) {
                return cachedUser;
            }

            LightweightUserAdapter lua = LightweightUserAdapter.fromString(
                    session,
                    getParentSession().getRealm(),
                    getUserSessionNotes().get(SESSION_NOTE_LIGHTWEIGHT_USER));
            session.setAttribute("authSession.user." + getParentSession().getId(), lua);
            lua.setUpdateHandler(lua1 -> {
                if (lua == lua1) { // Ensure there is no conflicting user model, only the latest lightweight
                    // user can be used
                    setUserSessionNote(SESSION_NOTE_LIGHTWEIGHT_USER, lua1.serialize());
                }
            });

            return lua;
        } else {
            return authenticationSession.getUserId() == null
                    ? null
                    : session.users().getUserById(getRealm(), authenticationSession.getUserId());
        }
    }

    @Override
    public void setAuthenticatedUser(UserModel user) {
        if (user == null) {
            authenticationSession.setUserId(null);
            setUserSessionNote(SESSION_NOTE_LIGHTWEIGHT_USER, null);
        } else {
            authenticationSession.setUserId(user.getId());

            if (isLightweightUser(user)) {
                LightweightUserAdapter lua = (LightweightUserAdapter) user;
                setUserSessionNote(SESSION_NOTE_LIGHTWEIGHT_USER, lua.serialize());
                lua.setUpdateHandler(lua1 -> {
                    if (lua == lua1) { // Ensure there is no conflicting user model, only the latest
                        // lightweight user can be used
                        setUserSessionNote(SESSION_NOTE_LIGHTWEIGHT_USER, lua1.serialize());
                    }
                });
            }
        }

        updated = true;
    }

    @Override
    public Set<String> getRequiredActions() {
        return new HashSet<>(authenticationSession.getRequiredActions());
    }

    @Override
    public void addRequiredAction(String action) {
        Objects.requireNonNull(action, "The provided action can't be null!");
        authenticationSession.getRequiredActions().add(action);
        updated = true;
    }

    @Override
    public void removeRequiredAction(String action) {
        Objects.requireNonNull(action, "The provided action can't be null!");
        authenticationSession.getRequiredActions().remove(action);
        updated = true;
    }

    @Override
    public void addRequiredAction(UserModel.RequiredAction action) {
        Objects.requireNonNull(action, "The provided action can't be null!");
        authenticationSession.getRequiredActions().add(action.name());
        updated = true;
    }

    @Override
    public void removeRequiredAction(UserModel.RequiredAction action) {
        Objects.requireNonNull(action, "The provided action can't be null!");
        authenticationSession.getRequiredActions().remove(action.name());
        updated = true;
    }

    @Override
    public void setUserSessionNote(String name, String value) {
        if (value == null) {
            authenticationSession.getUserNotes().remove(name);
        } else {
            authenticationSession.getUserNotes().put(name, value);
        }

        updated = true;
    }

    @Override
    public Map<String, String> getUserSessionNotes() {
        return authenticationSession.getUserNotes();
    }

    @Override
    public void clearUserSessionNotes() {
        authenticationSession.getUserNotes().clear();
        updated = true;
    }

    @Override
    public String getAuthNote(String name) {
        return authenticationSession.getAuthNotes().get(name);
    }

    @Override
    public void setAuthNote(String name, String value) {
        if (value == null) {
            removeAuthNote(name);
            return;
        }

        authenticationSession.getAuthNotes().put(name, value);
        updated = true;
    }

    @Override
    public void removeAuthNote(String name) {
        authenticationSession.getAuthNotes().remove(name);
        updated = true;
    }

    @Override
    public void clearAuthNotes() {
        authenticationSession.getAuthNotes().clear();
        updated = true;
    }

    @Override
    public String getClientNote(String name) {
        return authenticationSession.getClientNotes().get(name);
    }

    @Override
    public void setClientNote(String name, String value) {
        if (value == null) {
            removeClientNote(name);
            return;
        }

        authenticationSession.getClientNotes().put(name, value);
        updated = true;
    }

    @Override
    public void removeClientNote(String name) {
        authenticationSession.getClientNotes().remove(name);
        updated = true;
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
        updated = true;
    }

    @Override
    public String getRedirectUri() {
        return authenticationSession.getRedirectUri();
    }

    @Override
    public void setRedirectUri(String uri) {
        authenticationSession.setRedirectUri(uri);
        updated = true;
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
        updated = true;
    }

    @Override
    public String getProtocol() {
        return authenticationSession.getProtocol();
    }

    @Override
    public void setProtocol(String method) {
        authenticationSession.setProtocol(method);
        updated = true;
    }

    public void flush() {
        if (updated && !deleted) {
            authSessionRepository.insertOrUpdate(authenticationSession, parentSession.getEntity());
            updated = false;
        }
    }
}
