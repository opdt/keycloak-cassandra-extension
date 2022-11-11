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
package de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.StreamExtensions;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.MultivaluedHashMap;

import java.util.*;
import java.util.stream.Collectors;

@JBossLog
@RequiredArgsConstructor
public class CassandraUserRepository implements UserRepository {
  private final UserDao userDao;

  @Override
  public User findUserById(String realmId, String id) {
    return userDao.findById(realmId, id);
  }

  @Override
  public Set<String> findUserIdsByAttribute(
      String name, String value, int firstResult, int maxResult) {
    return StreamExtensions.paginated(
            userDao.findByAttribute(name, value), firstResult, maxResult)
        .map(AttributeToUserMapping::getUserId)
        .collect(Collectors.toSet());
  }

  @Override
  public List<User> findUsersByAttribute(String realmId, String name, String value) {
    return userDao.findByAttribute(name, value).all().stream()
        .map(AttributeToUserMapping::getUserId)
        .flatMap(id -> Optional.ofNullable(findUserById(realmId, id)).stream())
        .collect(Collectors.toList());
  }

  @Override
  public User findUserByAttribute(String realmId, String name, String value) {
    List<User> users = findUsersByAttribute(realmId, name, value);

    if (users.size() > 1) {
      throw new IllegalStateException("Found more than one user with attributeName " + name + " and value " + value);
    }

    if (users.isEmpty()) {
      return null;
    }

    return users.get(0);
  }

  @Override
  public MultivaluedHashMap<String, String> findAllUserAttributes(String userId) {
    List<UserToAttributeMapping> attributeMappings = userDao.findAllAttributes(userId).all();
    MultivaluedHashMap<String, String> result = new MultivaluedHashMap<>();

    attributeMappings.forEach(
        mapping -> result.addAll(mapping.getAttributeName(), mapping.getAttributeValues()));

    return result;
  }

  @Override
  public UserToAttributeMapping findUserAttribute(String userId, String attributeName) {
    return userDao.findAttribute(userId, attributeName);
  }

  @Override
  public void updateAttribute(UserToAttributeMapping UserAttributeMapping) {
    UserToAttributeMapping oldAttribute = userDao.findAttribute(UserAttributeMapping.getUserId(), UserAttributeMapping.getAttributeName());
    userDao.update(UserAttributeMapping);

    if (oldAttribute != null) {
      // Alte AttributeToUserMappings löschen, da die Values als Teil des PartitionKey nicht
      // geändert werden können
      oldAttribute
          .getAttributeValues()
          .forEach(value -> userDao.deleteAttributeToUserMapping(oldAttribute.getAttributeName(), value, oldAttribute.getUserId()));
    }

    UserAttributeMapping
        .getAttributeValues()
        .forEach(
            value -> {
              AttributeToUserMapping AttributeToUserMapping =
                  new AttributeToUserMapping(
                      UserAttributeMapping.getAttributeName(),
                      value,
                      UserAttributeMapping.getUserId());
              userDao.insert(AttributeToUserMapping);
            });
  }

  @Override
  public void deleteAttribute(String userId, String attributeName) {
    UserToAttributeMapping attribute = findUserAttribute(userId, attributeName);

    if (attribute == null) {
      return;
    }

    // Beide Mapping-Tabellen beachten!
    userDao.deleteAttribute(userId, attributeName);
    attribute
        .getAttributeValues()
        .forEach(value -> userDao.deleteAttributeToUserMapping(attributeName, value, userId));
  }

  @Override
  public void addRequiredAction(UserRequiredAction requiredAction) {
    userDao.insert(requiredAction);
  }

  @Override
  public void deleteRequiredAction(String userId, String requiredAction) {
    userDao.deleteRequiredAction(userId, requiredAction);
  }

  @Override
  public List<UserRequiredAction> findAllRequiredActions(String userId) {
    return userDao.findAllRequiredActions(userId).all();
  }

  @Override
  public void createOrUpdateUser(String realmId, User user) {
    userDao.insert(user);
    userDao.insert(new RealmToUserMapping(realmId, user.isServiceAccount(), user.getId()));
  }

  @Override
  public boolean deleteUser(String realmId, String userId) {
    User user = findUserById(realmId, userId);

    if (user == null) {
      return false;
    }

    userDao.delete(user);
    userDao.deleteRealmToUserMapping(realmId, user.isServiceAccount(), user.getId());

    deleteAllAttributes(user.getId());
    deleteAllRequiredActions(user.getId());

    return true;
  }

  @Override
  public void deleteAllAttributes(String userId) {
    MultivaluedHashMap<String, String> userAttributes = findAllUserAttributes(userId);
    userAttributes.entrySet().stream()
        .flatMap(entry -> entry.getValue().stream().map(value -> Map.entry(entry.getKey(), value)))
        .forEach(entry -> userDao.deleteAttributeToUserMapping(entry.getKey(), entry.getValue(), userId));

    userDao.deleteUserAttributes(userId);
  }

  @Override
  public void deleteAllRequiredActions(String userId) {
    userDao.deleteAllRequiredActions(userId);
  }

  @Override
  public void makeUserServiceAccount(User user, String realmId) {
    user.setServiceAccount(true);
    userDao.update(user);

    userDao.deleteRealmToUserMapping(realmId, false, user.getId());
    userDao.insert(new RealmToUserMapping(realmId, user.isServiceAccount(), user.getId()));
  }

  @Override
  public FederatedIdentity findFederatedIdentity(String userId, String identityProvider) {
    return userDao.findFederatedIdentity(userId, identityProvider);
  }

  @Override
  public FederatedIdentity findFederatedIdentityByBrokerUserId(
      String brokerUserId, String identityProvider) {
    FederatedIdentityToUserMapping identityToUserMapping =
        userDao.findFederatedIdentityByBrokerUserId(brokerUserId, identityProvider);

    if (identityToUserMapping == null) {
      return null;
    }

    return findFederatedIdentity(identityToUserMapping.getUserId(), identityProvider);
  }

  @Override
  public List<FederatedIdentity> findFederatedIdentities(String userId) {
    return userDao.findFederatedIdentities(userId).all();
  }

  @Override
  public void createOrUpdateFederatedIdentity(FederatedIdentity federatedIdentity) {
    userDao.update(federatedIdentity);
    FederatedIdentityToUserMapping identityToUserMapping =
        new FederatedIdentityToUserMapping(
            federatedIdentity.getBrokerUserId(),
            federatedIdentity.getIdentityProvider(),
            federatedIdentity.getUserId());
    userDao.update(identityToUserMapping);
  }

  @Override
  public boolean deleteFederatedIdentity(String userId, String identityProvider) {
    FederatedIdentity federatedIdentity = findFederatedIdentity(userId, identityProvider);

    if (federatedIdentity == null) {
      return false;
    }

    FederatedIdentityToUserMapping identityToUserMapping =
        userDao.findFederatedIdentityByBrokerUserId(
            federatedIdentity.getBrokerUserId(), identityProvider);

    userDao.delete(federatedIdentity);
    userDao.delete(identityToUserMapping);
    return true;
  }

  @Override
  public void createOrUpdateCredential(Credential credential) {
    userDao.update(credential);
  }

  @Override
  public List<Credential> findCredentials(String userId) {
    return userDao.findCredentials(userId).all().stream()
        .sorted(Comparator.comparing(Credential::getPriority))
        .collect(Collectors.toList());
  }

  @Override
  public Credential findCredential(String userId, String credId) {
    return findCredentials(userId).stream()
        .filter(cred -> cred.getId().equals(credId))
        .findFirst()
        .orElse(null);
  }

  @Override
  public boolean deleteCredential(String userId, String credId) {
    Credential credential = findCredential(userId, credId);

    if (credential != null) {
      userDao.delete(credential);
      return true;
    }

    return false;
  }

  @Override
  public Set<String> findUserIdsByRealmId(String realmId, int first, int max) {
    return StreamExtensions.paginated(userDao.findUsersByRealmId(realmId), first, max)
        .map(RealmToUserMapping::getUserId)
        .collect(Collectors.toSet());
  }

  @Override
  public long countUsersByRealmId(String realmId, boolean includeServiceAccounts) {
    if (includeServiceAccounts) {
      return userDao.countAllUsersByRealmId(realmId);
    } else {
      return userDao.countNonServiceAccountUsersByRealmId(realmId);
    }
  }

  @Override
  public Set<UserRealmRoleMapping> getRealmRolesByUserId(String userId) {
    return new HashSet<>(userDao.findRealmRolesByUserId(userId).all());
  }

  @Override
  public Set<UserClientRoleMapping> getAllClientRoleMappingsByUserId(String userId) {
    return new HashSet<>(userDao.findAllClientRoleMappingsByUserId(userId).all());
  }

  @Override
  public void removeRoleMapping(String userId, String roleId) {
    userDao.removeRoleMapping(new UserRealmRoleMapping(userId, roleId));
  }

  @Override
  public void removeClientRoleMapping(String userId, String clientId, String roleId) {
    userDao.removeClientRoleMapping(new UserClientRoleMapping(userId, clientId, roleId));
  }

  @Override
  public void addRealmRoleMapping(UserRealmRoleMapping roleMapping) {
    userDao.insert(roleMapping);
  }

  @Override
  public void addClientRoleMapping(UserClientRoleMapping roleMapping) {
    userDao.insert(roleMapping);
  }
}
