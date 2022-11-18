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
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.*;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.ObjectUtil;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.RoleUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@EqualsAndHashCode(of = "userEntity")
@JBossLog
@RequiredArgsConstructor
public abstract class CassandraUserAdapter implements UserModel {
  public static final String NOT_BEFORE = "notBefore";
  private final RealmModel realm;
  private final UserRepository userRepository;
  private final User userEntity;

  @Override
  public String getId() {
    return userEntity.getId();
  }

  @Override
  public String getUsername() {
    return userEntity.getUsername();
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

    userRepository.deleteUsernameSearchIndex(realm.getId(), userEntity);
    userEntity.setUsername(username);
    userEntity.setUsernameCaseInsensitive(KeycloakModelUtils.toLowerCaseSafe(username));
    userRepository.createOrUpdateUser(realm.getId(), userEntity);
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

    userRepository.deleteEmailSearchIndex(realm.getId(), userEntity);
    userEntity.setEmail(email);
    userRepository.createOrUpdateUser(realm.getId(), userEntity);
  }

  @Override
  public String getFirstName() {
    return userEntity.getFirstName();
  }

  @Override
  public void setFirstName(String firstName) {
    userEntity.setFirstName(firstName);
    userRepository.createOrUpdateUser(realm.getId(), userEntity);
  }

  @Override
  public String getLastName() {
    return userEntity.getLastName();
  }

  @Override
  public void setLastName(String lastName) {
    userEntity.setLastName(lastName);
    userRepository.createOrUpdateUser(realm.getId(), userEntity);
  }

  @Override
  public Long getCreatedTimestamp() {
    return userEntity.getCreatedTimestamp().getEpochSecond();
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
    userRepository.createOrUpdateUser(realm.getId(), userEntity);
  }

  @Override
  public void setEnabled(boolean enabled) {
    userEntity.setEnabled(enabled);
    userRepository.createOrUpdateUser(realm.getId(), userEntity);
  }

  @Override
  public Map<String, List<String>> getAttributes() {
    log.debugv("get attributes: realm={0} userId={1}", realm.getId(), userEntity.getId());

    return userEntity.getAttributes();
  }

  @Override
  public void setAttribute(String name, List<String> values) {
    log.debugv(realm.getId(), userEntity.getId(), name, values);

    userEntity.getAttributes().put(name, values);
    userRepository.createOrUpdateUser(realm.getId(), userEntity);
  }

  @Override
  public void setSingleAttribute(String name, String value) {
    log.debugv(realm.getId(), userEntity.getId(), name, value);

    userEntity.getAttributes().put(name, Collections.singletonList(value));
    userRepository.createOrUpdateUser(realm.getId(), userEntity);
  }

  public List<String> getAttribute(String name) {
    return userEntity.getAttribute(name);
  }

  @Override
  public String getFirstAttribute(String name) {
    List<String> attributeValues = getAttribute(name);
    if (attributeValues == null || attributeValues.isEmpty()) {
      return null;
    }
    return attributeValues.get(0);
  }

  @Override
  public void removeAttribute(String name) {
    log.debugv("remove attribute: realm={0} userId={1} name={2}", realm.getId(), userEntity.getId(), name);

    userEntity.getAttributes().remove(name);
    userRepository.createOrUpdateUser(realm.getId(), userEntity);
  }

  @Override
  public void grantRole(RoleModel role) {
    log.debugv(
        "grant role mapping: realm={0} userId={1} role={2}",
        realm.getId(), userEntity.getId(), role.getName());

    if (role.isClientRole()) {
      UserClientRoleMapping userEntityClientRoleMapping =
          new UserClientRoleMapping(userEntity.getId(), role.getContainer().getId(), role.getId());
      userRepository.addClientRoleMapping(userEntityClientRoleMapping);
    } else {
      UserRealmRoleMapping userEntityRoleMapping = new UserRealmRoleMapping(userEntity.getId(), role.getId());
      userRepository.addRealmRoleMapping(userEntityRoleMapping);
    }
  }

  @Override
  public void deleteRoleMapping(RoleModel role) {
    log.debugv(
        "delete role mapping: realm={0} userId={1} role={2}",
        realm.getId(), userEntity.getId(), role.getName());

    if (role.isClientRole()) {
      userRepository.removeClientRoleMapping(
          userEntity.getId(), role.getContainer().getId(), role.getId());
    } else {
      userRepository.removeRoleMapping(userEntity.getId(), role.getId());
    }
  }

  @Override
  public void addRequiredAction(RequiredAction action) {
    userRepository.addRequiredAction(new UserRequiredAction(userEntity.getId(), action.name()));
  }

  @Override
  public void addRequiredAction(String action) {
    userRepository.addRequiredAction(new UserRequiredAction(userEntity.getId(), action));
  }

  @Override
  public void removeRequiredAction(RequiredAction action) {
    userRepository.deleteRequiredAction(userEntity.getId(), action.name());
  }

  @Override
  public void removeRequiredAction(String action) {
    userRepository.deleteRequiredAction(userEntity.getId(), action);
  }

  @Override
  public String getFederationLink() {
    return userEntity.getFederationLink();
  }

  @Override
  public void setFederationLink(String link) {
    userRepository.deleteFederationLinkSearchIndex(realm.getId(), userEntity);
    userEntity.setFederationLink(link);
    userRepository.createOrUpdateUser(realm.getId(), userEntity);
  }

  @Override
  public String getServiceAccountClientLink() {
    return userEntity.getServiceAccountClientLink();
  }

  @Override
  public void setServiceAccountClientLink(String clientInternalId) {
    userRepository.deleteServiceAccountLinkSearchIndex(realm.getId(), userEntity);
    userEntity.setServiceAccountClientLink(clientInternalId);
    userRepository.makeUserServiceAccount(userEntity, realm.getId());
  }


  @Override
  public Stream<String> getAttributeStream(String name) {
    return userEntity.getAttribute(name).stream();
  }

  @Override
  public Stream<String> getRequiredActionsStream() {
    List<UserRequiredAction> allRequiredActions = userRepository.findAllRequiredActions(userEntity.getId());
    if (allRequiredActions == null) {
      return Stream.empty();
    }
    return allRequiredActions.stream().map(UserRequiredAction::getRequiredAction);
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
    List<String> realmRoleIds =
        userRepository.getRealmRolesByUserId(userEntity.getId()).stream()
            .map(UserRealmRoleMapping::getRoleId)
            .collect(Collectors.toList());
    roleIds.addAll(realmRoleIds);

    List<String> clientRoleIds =
        userRepository.getAllClientRoleMappingsByUserId(userEntity.getId()).stream()
            .filter(role -> realm
                .getClientsStream()
                .anyMatch(client -> client.getId().equals(role.getClientId())))
            .map(UserClientRoleMapping::getRoleId)
            .collect(Collectors.toList());
    roleIds.addAll(clientRoleIds);

    return roleIds.stream().map(realm::getRoleById);
  }

  public abstract boolean checkEmailUniqueness(RealmModel realm, String email);

  public abstract boolean checkUsernameUniqueness(RealmModel realm, String username);

  @Override
  public abstract SubjectCredentialManager credentialManager();
}
