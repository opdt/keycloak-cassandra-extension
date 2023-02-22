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

import de.arbeitsagentur.opdt.keycloak.cassandra.AttributeTypes;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalModelAdapter;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.User;
import lombok.EqualsAndHashCode;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.common.util.ObjectUtil;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.RoleUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EqualsAndHashCode(callSuper = true)
@JBossLog
public abstract class CassandraUserAdapter extends TransactionalModelAdapter<User> implements UserModel {
    public static final String NOT_BEFORE = AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX + "notBefore";

    @EqualsAndHashCode.Exclude
    private final RealmModel realm;

    @EqualsAndHashCode.Exclude
    private final UserRepository userRepository;

    public CassandraUserAdapter(User entity, RealmModel realm, UserRepository userRepository) {
        super(entity);
        this.realm = realm;
        this.userRepository = userRepository;
    }

    public RealmModel getRealm() {
        return realm;
    }

    @Override
    public String getId() {
        return entity.getId();
    }

    @Override
    public String getUsername() {
        return entity.getUsername();
    }

    public boolean hasUsername(String toCompare) {
        if (toCompare == null) {
            return false;
        }

        return KeycloakModelUtils.isUsernameCaseSensitive(realm)
            ? KeycloakModelUtils.toLowerCaseSafe(toCompare).equals(entity.getUsernameCaseInsensitive())
            : toCompare.equals(entity.getUsername());
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
            ? entity.getUsername()
            : entity.getUsernameCaseInsensitive();

        // Do not continue if current username of entity is the requested username
        if (usernameToCompare.equals(currentUsername)) return;

        if (checkUsernameUniqueness(realm, username)) {
            throw new ModelDuplicateException("A user with username " + username + " already exists");
        }

        User userCopy = entity.toBuilder().build();
        entity.setUsername(username);
        entity.setUsernameCaseInsensitive(KeycloakModelUtils.toLowerCaseSafe(username));
        markUpdated(() -> userRepository.deleteUsernameSearchIndex(realm.getId(), userCopy));
    }

    @Override
    public String getEmail() {
        return entity.getEmail();
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

        User userCopy = entity.toBuilder().build();
        entity.setEmail(email);

        markUpdated(() -> userRepository.deleteEmailSearchIndex(realm.getId(), userCopy));
    }

    @Override
    public String getFirstName() {
        return entity.getFirstName();
    }

    @Override
    public void setFirstName(String firstName) {
        entity.setFirstName(firstName);
        markUpdated();
    }

    @Override
    public String getLastName() {
        return entity.getLastName();
    }

    @Override
    public void setLastName(String lastName) {
        entity.setLastName(lastName);
        markUpdated();
    }

    @Override
    public Long getCreatedTimestamp() {
        return entity.getCreatedTimestamp().getEpochSecond() * 1000; // Milliseconds
    }

    @Override
    public void setCreatedTimestamp(Long timestamp) {
        // NOOP
    }

    @Override
    public boolean isEnabled() {
        return entity.getEnabled() != null && entity.getEnabled();
    }

    @Override
    public boolean isEmailVerified() {
        return entity.getEmailVerified() != null && entity.getEmailVerified();
    }

    @Override
    public void setEmailVerified(boolean verified) {
        entity.setEmailVerified(verified);
        markUpdated();
    }

    @Override
    public void setEnabled(boolean enabled) {
        entity.setEnabled(enabled);
        markUpdated();

    }

    @Override
    public Map<String, List<String>> getAttributes() {
        log.debugv("get attributes: realm={0} userId={1}", realm.getId(), entity.getId());

        Map<String, List<String>> attributes = getAllAttributes();
        MultivaluedHashMap<String, String> result = attributes == null ? new MultivaluedHashMap<>() : new MultivaluedHashMap<>(attributes);
        result.add(UserModel.FIRST_NAME, entity.getFirstName());
        result.add(UserModel.LAST_NAME, entity.getLastName());
        result.add(UserModel.EMAIL, entity.getEmail());
        result.add(UserModel.USERNAME, entity.getUsername());

        return result;
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        String valueToSet = values != null && !values.isEmpty() ? values.get(0) : null;
        if (setSpecialAttributeValue(name, valueToSet)) return;

        User userCopy = entity.toBuilder().build();
        super.setAttribute(name, values);

        addPostUpdateTask(() -> userRepository.deleteAttributeSearchIndex(realm.getId(), userCopy, name));
    }

    @Override
    public void setSingleAttribute(String name, String value) {
        if (setSpecialAttributeValue(name, value)) return;

        User userCopy = entity.toBuilder().build();
        super.setAttribute(name, value);

        addPostUpdateTask(() -> userRepository.deleteAttributeSearchIndex(realm.getId(), userCopy, name));
    }

    @Override
    public String getFirstAttribute(String name) {
        return getSpecialAttributeValue(name).orElseGet(() -> getAttribute(name));
    }

    @Override
    public void removeAttribute(String name) {
        User userCopy = entity.toBuilder().build();
        super.removeAttribute(name);

        addPostUpdateTask(() -> userRepository.deleteAttributeSearchIndex(realm.getId(), userCopy, name));
    }

    @Override
    public void grantRole(RoleModel role) {
        log.debugv("grant role mapping: realm={0} userId={1} role={2}", realm.getId(), entity.getId(), role.getName());

        if (role.isClientRole()) {
            Set<String> clientRoles = entity.getClientRoles().getOrDefault(role.getContainerId(), new HashSet<>());
            clientRoles.add(role.getId());
            entity.getClientRoles().put(role.getContainerId(), clientRoles);
        } else {
            entity.getRealmRoles().add(role.getId());
        }

        markUpdated();
    }

    @Override
    public void deleteRoleMapping(RoleModel role) {
        log.debugv(
            "delete role mapping: realm={0} userId={1} role={2}",
            realm.getId(), entity.getId(), role.getName());

        if (role.isClientRole()) {
            Set<String> clientRoles = entity.getClientRoles().getOrDefault(role.getContainerId(), new HashSet<>());
            clientRoles.remove(role.getId());
            entity.getClientRoles().put(role.getContainerId(), clientRoles);
        } else {
            entity.getRealmRoles().remove(role.getId());
        }

        markUpdated();
    }

    @Override
    public void addRequiredAction(RequiredAction action) {
        entity.getRequiredActions().add(action.name());
        markUpdated();
    }

    @Override
    public void addRequiredAction(String action) {
        entity.getRequiredActions().add(action);
        markUpdated();
    }

    @Override
    public void removeRequiredAction(RequiredAction action) {
        entity.getRequiredActions().remove(action.name());
        markUpdated();
    }

    @Override
    public void removeRequiredAction(String action) {
        entity.getRequiredActions().remove(action);
        markUpdated();
    }

    @Override
    public String getFederationLink() {
        return entity.getFederationLink();
    }

    @Override
    public void setFederationLink(String link) {
        User userCopy = entity.toBuilder().build();
        entity.setFederationLink(link);

        markUpdated(() -> userRepository.deleteFederationLinkSearchIndex(realm.getId(), userCopy));
    }

    @Override
    public String getServiceAccountClientLink() {
        return entity.getServiceAccountClientLink();
    }

    @Override
    public void setServiceAccountClientLink(String clientInternalId) {
        User userCopy = entity.toBuilder().build();
        entity.setServiceAccountClientLink(clientInternalId);

        markUpdated(() -> userRepository.deleteServiceAccountLinkSearchIndex(realm.getId(), userCopy));
    }


    @Override
    public Stream<String> getAttributeStream(String name) {
        return getSpecialAttributeValue(name).map(Collections::singletonList).orElseGet(() -> entity.getAttribute(name)).stream();
    }

    @Override
    public Stream<String> getRequiredActionsStream() {
        return entity.getRequiredActions().stream();
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
        log.debugv("get role mappings: realm={0} userId={1}", realm.getId(), entity.getId());

        List<String> roleIds = new ArrayList<>();
        roleIds.addAll(entity.getRealmRoles());
        roleIds.addAll(entity.getClientRoles().entrySet().stream().flatMap(e -> e.getValue().stream()).collect(Collectors.toSet()));

        // TODO: Remove and save Role-mappings for no longer existent roles...
        return roleIds.stream().map(realm::getRoleById).filter(Objects::nonNull);
    }

    public abstract boolean checkEmailUniqueness(RealmModel realm, String email);

    public abstract boolean checkUsernameUniqueness(RealmModel realm, String username);

    @Override
    public abstract SubjectCredentialManager credentialManager();

    public boolean delete() {
        markDeleted(); // no more updates for this user
        return userRepository.deleteUser(entity.getRealmId(), entity.getId());
    }

    @Override
    protected void flushChanges() {
        userRepository.insertOrUpdate(entity);

        if (entity.getServiceAccountClientLink() != null && !entity.isServiceAccount()) {
            userRepository.makeUserServiceAccount(entity, realm.getId());
        }
    }

    private void setVersion(long version) {
        entity.setVersion(version);
        markUpdated();
    }

    private Optional<String> getSpecialAttributeValue(String name) {
        if (UserModel.FIRST_NAME.equals(name)) {
            return Optional.ofNullable(entity.getFirstName());
        } else if (UserModel.LAST_NAME.equals(name)) {
            return Optional.ofNullable(entity.getLastName());
        } else if (UserModel.EMAIL.equals(name)) {
            return Optional.ofNullable(entity.getEmail());
        } else if (UserModel.USERNAME.equals(name)) {
            return Optional.ofNullable(entity.getUsername());
        }

        return Optional.empty();
    }

    private boolean setSpecialAttributeValue(String name, String value) {
        if (UserModel.FIRST_NAME.equals(name)) {
            entity.setFirstName(value);
            return true;
        } else if (UserModel.LAST_NAME.equals(name)) {
            entity.setLastName(value);
            return true;
        } else if (UserModel.EMAIL.equals(name)) {
            setEmail(value);
            return true;
        } else if (UserModel.USERNAME.equals(name)) {
            setUsername(value);
            return true;
        }

        return false;
    }
}
