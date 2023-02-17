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
package de.arbeitsagentur.opdt.keycloak.cassandra.user;

import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.User;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.common.util.ObjectUtil;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.RoleUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EqualsAndHashCode(of = "userEntity")
@JBossLog
@RequiredArgsConstructor
public abstract class CassandraUserAdapter implements UserModel {
    public static final String NOT_BEFORE = "notBefore";
    public static final String ENTITY_VERSION = "entityVersion";
    private final RealmModel realm;
    private final UserRepository userRepository;
    private final User userEntity;

    private final List<Runnable> postUpdateTasks = new ArrayList<>();
    private boolean updated = false;
    private boolean deleted = false;

    public RealmModel getRealm() {
        return realm;
    }

    @Override
    public String getId() {
        return userEntity.getId();
    }

    @Override
    public String getUsername() {
        return userEntity.getUsername();
    }

    public boolean hasUsername(String toCompare) {
        if (toCompare == null) {
            return false;
        }

        return KeycloakModelUtils.isUsernameCaseSensitive(realm)
            ? KeycloakModelUtils.toLowerCaseSafe(toCompare).equals(userEntity.getUsernameCaseInsensitive())
            : toCompare.equals(userEntity.getUsername());
    }

    @Override
    public void setUsername(String username) {
        if (username == null) {
            return;
        }

        String usernameToCompare = KeycloakModelUtils.isUsernameCaseSensitive(realm)
            ? username
            : KeycloakModelUtils.toLowerCaseSafe(username);

        String currentUsername = KeycloakModelUtils.isUsernameCaseSensitive(realm)
            ? userEntity.getUsername()
            : userEntity.getUsernameCaseInsensitive();

        // Do not continue if current username of entity is the requested username
        if (usernameToCompare.equals(currentUsername)) return;

        if (checkUsernameUniqueness(realm, username)) {
            throw new ModelDuplicateException("A user with username " + username + " already exists");
        }

        User userCopy = userEntity.toBuilder().build();
        userEntity.setUsername(username);
        userEntity.setUsernameCaseInsensitive(KeycloakModelUtils.toLowerCaseSafe(username));
        updated = true;

        postUpdateTasks.add(() -> userRepository.deleteUsernameSearchIndex(realm.getId(), userCopy));
    }

    @Override
    public String getEmail() {
        return userEntity.getEmail();
    }

    @Override
    public void setEmail(String email) {
        email = KeycloakModelUtils.toLowerCaseSafe(email);
        if (email != null) {
            if (email.equals(getEmail())) {
                return;
            }
            if (ObjectUtil.isBlank(email)) {
                email = null;
            }
        }
        boolean duplicatesAllowed = realm.isDuplicateEmailsAllowed();

        if (!duplicatesAllowed && email != null && checkEmailUniqueness(realm, email)) {
            throw new ModelDuplicateException("A user with email " + email + " already exists");
        }

        User userCopy = userEntity.toBuilder().build();
        userEntity.setEmail(email);
        updated = true;

        postUpdateTasks.add(() -> userRepository.deleteEmailSearchIndex(realm.getId(), userCopy));
    }

    @Override
    public String getFirstName() {
        return userEntity.getFirstName();
    }

    @Override
    public void setFirstName(String firstName) {
        userEntity.setFirstName(firstName);
        updated = true;
    }

    @Override
    public String getLastName() {
        return userEntity.getLastName();
    }

    @Override
    public void setLastName(String lastName) {
        userEntity.setLastName(lastName);
        updated = true;
    }

    @Override
    public Long getCreatedTimestamp() {
        return userEntity.getCreatedTimestamp().getEpochSecond() * 1000; // Milliseconds
    }

    @Override
    public void setCreatedTimestamp(Long timestamp) {
        // NOOP
    }

    @Override
    public boolean isEnabled() {
        return userEntity.getEnabled() != null && userEntity.getEnabled();
    }

    @Override
    public boolean isEmailVerified() {
        return userEntity.getEmailVerified() != null && userEntity.getEmailVerified();
    }

    @Override
    public void setEmailVerified(boolean verified) {
        userEntity.setEmailVerified(verified);
        updated = true;
    }

    @Override
    public void setEnabled(boolean enabled) {
        userEntity.setEnabled(enabled);
        updated = true;

    }

    @Override
    public Map<String, List<String>> getAttributes() {
        log.debugv("get attributes: realm={0} userId={1}", realm.getId(), userEntity.getId());

        Map<String, List<String>> attributes = userEntity.getAttributes();
        MultivaluedHashMap<String, String> result = attributes == null ? new MultivaluedHashMap<>() : new MultivaluedHashMap<>(attributes);
        result.add(UserModel.FIRST_NAME, userEntity.getFirstName());
        result.add(UserModel.LAST_NAME, userEntity.getLastName());
        result.add(UserModel.EMAIL, userEntity.getEmail());
        result.add(UserModel.USERNAME, userEntity.getUsername());

        return result;
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        log.debugv(realm.getId(), userEntity.getId(), name, values);

        if (values == null) {
            return;
        }

        String valueToSet = !values.isEmpty() ? values.get(0) : null;
        if (setSpecialAttributeValue(name, valueToSet)) return;

        User userCopy = userEntity.toBuilder().build();
        userEntity.getAttributes().put(name, values);
        updated = true;

        postUpdateTasks.add(() -> userRepository.deleteAttributeSearchIndex(realm.getId(), userCopy, name));
    }

    @Override
    public void setSingleAttribute(String name, String value) {
        log.debugv(realm.getId(), userEntity.getId(), name, value);

        if (value == null) {
            return;
        }

        if (setSpecialAttributeValue(name, value)) return;

        User userCopy = userEntity.toBuilder().build();
        userEntity.getAttributes().put(name, Collections.singletonList(value));
        updated = true;

        postUpdateTasks.add(() -> userRepository.deleteAttributeSearchIndex(realm.getId(), userCopy, name));
    }

    @Override
    public String getFirstAttribute(String name) {
        return getSpecialAttributeValue(name).orElseGet(() -> {
            List<String> attributeValues = userEntity.getAttribute(name);
            if (attributeValues == null || attributeValues.isEmpty()) {
                return null;
            }
            return attributeValues.get(0);
        });
    }

    @Override
    public void removeAttribute(String name) {
        log.debugv("remove attribute: realm={0} userId={1} name={2}", realm.getId(), userEntity.getId(), name);

        User userCopy = userEntity.toBuilder().build();
        userEntity.getAttributes().remove(name);
        updated = true;

        postUpdateTasks.add(() -> userRepository.deleteAttributeSearchIndex(realm.getId(), userCopy, name));
    }

    @Override
    public void grantRole(RoleModel role) {
        log.debugv("grant role mapping: realm={0} userId={1} role={2}", realm.getId(), userEntity.getId(), role.getName());

        if (role.isClientRole()) {
            Set<String> clientRoles = userEntity.getClientRoles().getOrDefault(role.getContainerId(), new HashSet<>());
            clientRoles.add(role.getId());
            userEntity.getClientRoles().put(role.getContainerId(), clientRoles);
        } else {
            userEntity.getRealmRoles().add(role.getId());
        }

        updated = true;
    }

    @Override
    public void deleteRoleMapping(RoleModel role) {
        log.debugv(
            "delete role mapping: realm={0} userId={1} role={2}",
            realm.getId(), userEntity.getId(), role.getName());

        if (role.isClientRole()) {
            Set<String> clientRoles = userEntity.getClientRoles().getOrDefault(role.getContainerId(), new HashSet<>());
            clientRoles.remove(role.getId());
            userEntity.getClientRoles().put(role.getContainerId(), clientRoles);
        } else {
            userEntity.getRealmRoles().remove(role.getId());
        }

        updated = true;
    }

    @Override
    public void addRequiredAction(RequiredAction action) {
        userEntity.getRequiredActions().add(action.name());
        updated = true;
    }

    @Override
    public void addRequiredAction(String action) {
        userEntity.getRequiredActions().add(action);
        updated = true;
    }

    @Override
    public void removeRequiredAction(RequiredAction action) {
        userEntity.getRequiredActions().remove(action.name());
        updated = true;
    }

    @Override
    public void removeRequiredAction(String action) {
        userEntity.getRequiredActions().remove(action);
        updated = true;
    }

    @Override
    public String getFederationLink() {
        return userEntity.getFederationLink();
    }

    @Override
    public void setFederationLink(String link) {
        User userCopy = userEntity.toBuilder().build();
        userEntity.setFederationLink(link);
        updated = true;

        postUpdateTasks.add(() -> userRepository.deleteFederationLinkSearchIndex(realm.getId(), userCopy));
    }

    @Override
    public String getServiceAccountClientLink() {
        return userEntity.getServiceAccountClientLink();
    }

    @Override
    public void setServiceAccountClientLink(String clientInternalId) {
        User userCopy = userEntity.toBuilder().build();
        userEntity.setServiceAccountClientLink(clientInternalId);
        updated = true;

        postUpdateTasks.add(() -> userRepository.deleteServiceAccountLinkSearchIndex(realm.getId(), userCopy));
    }


    @Override
    public Stream<String> getAttributeStream(String name) {
        return getSpecialAttributeValue(name).map(Collections::singletonList).orElseGet(() -> userEntity.getAttribute(name)).stream();
    }

    @Override
    public Stream<String> getRequiredActionsStream() {
        return userEntity.getRequiredActions().stream();
    }

    @Override
    public Stream<GroupModel> getGroupsStream() {
        // TODO: Implement
        return Stream.empty();
    }

    @Override
    public void joinGroup(GroupModel group) {
        // TODO: Implement
    }

    @Override
    public void leaveGroup(GroupModel group) {
        // TODO: Implement
    }

    @Override
    public boolean isMemberOf(GroupModel group) {
        // TODO: Implement
        return false;
    }

    @Override
    public Stream<RoleModel> getRealmRoleMappingsStream() {
        return getRoleMappingsStream().filter(RoleUtils::isRealmRole);
    }

    @Override
    public Stream<RoleModel> getClientRoleMappingsStream(ClientModel app) {
        return getRoleMappingsStream().filter(r -> RoleUtils.isClientRole(r, app));
    }

    @Override
    public boolean hasDirectRole(RoleModel role) {
        return getRoleMappingsStream().map(RoleModel::getId).anyMatch(r -> r.equals(role.getId()));
    }

    @Override
    public boolean hasRole(RoleModel role) {
        return RoleUtils.hasRole(getRoleMappingsStream(), role)
            || RoleUtils.hasRoleFromGroup(getGroupsStream(), role, true);
    }

    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        log.debugv("get role mappings: realm={0} userId={1}", realm.getId(), userEntity.getId());

        List<String> roleIds = new ArrayList<>();
        roleIds.addAll(userEntity.getRealmRoles());
        roleIds.addAll(userEntity.getClientRoles().entrySet().stream().flatMap(e -> e.getValue().stream()).collect(Collectors.toSet()));

        // TODO: Remove and save Role-mappings for no longer existent roles...
        return roleIds.stream().map(realm::getRoleById).filter(Objects::nonNull);
    }

    public abstract boolean checkEmailUniqueness(RealmModel realm, String email);

    public abstract boolean checkUsernameUniqueness(RealmModel realm, String username);

    @Override
    public abstract SubjectCredentialManager credentialManager();

    public boolean delete() {
        deleted = true; // no more updates for this user
        return userRepository.deleteUser(userEntity.getRealmId(), userEntity.getId());
    }

    public void flush() {
        if (updated && !deleted) {
            userRepository.createOrUpdateUser(realm.getId(), userEntity);

            if (userEntity.getServiceAccountClientLink() != null && !userEntity.isServiceAccount()) {
                userRepository.makeUserServiceAccount(userEntity, realm.getId());
            }

            postUpdateTasks.forEach(Runnable::run);

            postUpdateTasks.clear();
            updated = false;
        }
    }

    private void setVersion(long version) {
        userEntity.setVersion(version);
        updated = true;
    }

    private Optional<String> getSpecialAttributeValue(String name) {
        if (UserModel.FIRST_NAME.equals(name)) {
            return Optional.ofNullable(userEntity.getFirstName());
        } else if (UserModel.LAST_NAME.equals(name)) {
            return Optional.ofNullable(userEntity.getLastName());
        } else if (UserModel.EMAIL.equals(name)) {
            return Optional.ofNullable(userEntity.getEmail());
        } else if (UserModel.USERNAME.equals(name)) {
            return Optional.ofNullable(userEntity.getUsername());
        } else if(ENTITY_VERSION.equals(name)) {
            return Optional.of(String.valueOf(userEntity.getVersion()));
        }

        return Optional.empty();
    }

    private boolean setSpecialAttributeValue(String name, String value) {
        if (UserModel.FIRST_NAME.equals(name)) {
            userEntity.setFirstName(value);
            return true;
        } else if (UserModel.LAST_NAME.equals(name)) {
            userEntity.setLastName(value);
            return true;
        } else if (UserModel.EMAIL.equals(name)) {
            setEmail(value);
            return true;
        } else if (UserModel.USERNAME.equals(name)) {
            setUsername(value);
            return true;
        } else if (ENTITY_VERSION.equals(name)) {
            setVersion(Long.parseLong(value));
            return true;
        }

        return false;
    }
}
