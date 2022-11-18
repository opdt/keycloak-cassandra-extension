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

import java.util.*;
import java.util.stream.Collectors;

@JBossLog
@RequiredArgsConstructor
public class CassandraUserRepository implements UserRepository {
  private static final String USERNAME = "username";
  private static final String USERNAME_CASE_INSENSITIVE = "usernameCaseInsensitive";
  private static final String EMAIL = "email";
  private static final String SERVICE_ACCOUNT_LINK = "serviceAccountLink";
  private static final String FEDERATED_IDENTITY_LINK = "federatedIdentityLink";
  private final UserDao userDao;

  @Override
  public List<User> findAllUsers() {
    return userDao.findAll().all();
  }

  @Override
  public User findUserById(String realmId, String id) {
    return userDao.findById(realmId, id);
  }

  @Override
  public User findUserByEmail(String realmId, String email) {
    if(email == null) {
      return null;
    }

    UserSearchIndex user = userDao.findUser(realmId, EMAIL, email);
    if (user == null) {
      return null;
    }

    return findUserById(realmId, user.getUserId());
  }

  @Override
  public User findUserByUsername(String realmId, String username) {
    if(username == null) {
      return null;
    }

    UserSearchIndex user = userDao.findUser(realmId, USERNAME, username);
    if (user == null) {
      return null;
    }

    return findUserById(realmId, user.getUserId());
  }

  @Override
  public User findUserByUsernameCaseInsensitive(String realmId, String username) {
    if(username == null) {
      return null;
    }

    UserSearchIndex user = userDao.findUser(realmId, USERNAME_CASE_INSENSITIVE, username);
    if (user == null) {
      return null;
    }

    return findUserById(realmId, user.getUserId());
  }

  @Override
  public User findUserByServiceAccountLink(String realmId, String serviceAccountLink) {
    if(serviceAccountLink == null) {
      return null;
    }

    UserSearchIndex user = userDao.findUser(realmId, SERVICE_ACCOUNT_LINK, serviceAccountLink);
    if (user == null) {
      return null;
    }

    return findUserById(realmId, user.getUserId());
  }

  @Override
  public User findUserByFederatedIdentityLink(String realmId, String federationLink) {
    if(federationLink == null) {
      return null;
    }

    UserSearchIndex user = userDao.findUser(realmId, FEDERATED_IDENTITY_LINK, federationLink);
    if (user == null) {
      return null;
    }

    return findUserById(realmId, user.getUserId());
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
  public void deleteUsernameSearchIndex(String realmId, User user) {
    if(user.getUsername() != null) {
      userDao.deleteIndex(realmId, USERNAME, user.getUsername());
    }

    if(user.getUsernameCaseInsensitive() != null) {
      userDao.deleteIndex(realmId, USERNAME_CASE_INSENSITIVE, user.getUsernameCaseInsensitive());
    }
  }

  @Override
  public void deleteEmailSearchIndex(String realmId, User user) {
    if(user.getEmail() != null) {
      userDao.deleteIndex(realmId, EMAIL, user.getEmail());
    }
  }

  @Override
  public void deleteFederationLinkSearchIndex(String realmId, User user) {
    if(user.getFederationLink() != null) {
      userDao.deleteIndex(realmId, FEDERATED_IDENTITY_LINK, user.getFederationLink());
    }
  }

  @Override
  public void deleteServiceAccountLinkSearchIndex(String realmId, User user) {
    if(user.getServiceAccountClientLink() != null) {
      userDao.deleteIndex(realmId, SERVICE_ACCOUNT_LINK, user.getServiceAccountClientLink());
    }
  }
  @Override
  public void createOrUpdateUser(String realmId, User user) {
    userDao.insert(user);
    userDao.insert(new RealmToUserMapping(realmId, user.isServiceAccount(), user.getId()));

    if(user.getUsername() != null) {
      userDao.insertOrUpdate(new UserSearchIndex(realmId, USERNAME, user.getUsername(), user.getId()));
    }

    if(user.getUsernameCaseInsensitive() != null) {
      userDao.insertOrUpdate(new UserSearchIndex(realmId, USERNAME_CASE_INSENSITIVE, user.getUsernameCaseInsensitive(), user.getId()));
    }

    if(user.getEmail() != null) {
      userDao.insertOrUpdate(new UserSearchIndex(realmId, EMAIL, user.getEmail(), user.getId()));
    }

    if(user.getServiceAccountClientLink() != null) {
      userDao.insertOrUpdate(new UserSearchIndex(realmId, SERVICE_ACCOUNT_LINK, user.getServiceAccountClientLink(), user.getId()));
    }

    if(user.getFederationLink() != null) {
      userDao.insertOrUpdate(new UserSearchIndex(realmId, FEDERATED_IDENTITY_LINK, user.getFederationLink(), user.getId()));
    }
  }

  @Override
  public boolean deleteUser(String realmId, String userId) {
    User user = findUserById(realmId, userId);

    if (user == null) {
      return false;
    }

    userDao.delete(user);
    userDao.deleteRealmToUserMapping(realmId, user.isServiceAccount(), user.getId());

    deleteAllRequiredActions(user.getId());
    deleteUsernameSearchIndex(realmId, user);
    deleteEmailSearchIndex(realmId, user);
    deleteServiceAccountLinkSearchIndex(realmId, user);
    deleteFederationLinkSearchIndex(realmId, user);

    return true;
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

    userDao.insertOrUpdate(new UserSearchIndex(realmId, SERVICE_ACCOUNT_LINK, user.getServiceAccountClientLink(), user.getId()));
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
