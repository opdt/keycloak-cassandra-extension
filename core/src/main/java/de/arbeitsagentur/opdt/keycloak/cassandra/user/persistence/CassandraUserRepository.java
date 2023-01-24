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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class CassandraUserRepository implements UserRepository {
    private static final String USERNAME = "username";
    private static final String USERNAME_CASE_INSENSITIVE = "usernameCaseInsensitive";
    private static final String EMAIL = "email";
    private static final String SERVICE_ACCOUNT_LINK = "serviceAccountLink";
    private static final String FEDERATION_LINK = "federationLink";
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
        if (email == null) {
            return null;
        }

        UserSearchIndex user = userDao.findUsers(realmId, EMAIL, email).all().stream().findFirst().orElse(null);
        if (user == null) {
            return null;
        }

        return findUserById(realmId, user.getUserId());
    }

    @Override
    public User findUserByUsername(String realmId, String username) {
        if (username == null) {
            return null;
        }

        UserSearchIndex user = userDao.findUsers(realmId, USERNAME, username).all().stream().findFirst().orElse(null);
        if (user == null) {
            return null;
        }

        return findUserById(realmId, user.getUserId());
    }

    @Override
    public User findUserByUsernameCaseInsensitive(String realmId, String username) {
        if (username == null) {
            return null;
        }

        UserSearchIndex user = userDao.findUsers(realmId, USERNAME_CASE_INSENSITIVE, username).all().stream().findFirst().orElse(null);

        if (user == null) {
            return null;
        }

        return findUserById(realmId, user.getUserId());
    }

    @Override
    public User findUserByServiceAccountLink(String realmId, String serviceAccountLink) {
        if (serviceAccountLink == null) {
            return null;
        }

        UserSearchIndex user = userDao.findUsers(realmId, SERVICE_ACCOUNT_LINK, serviceAccountLink).all().stream().findFirst().orElse(null);

        if (user == null) {
            return null;
        }

        return findUserById(realmId, user.getUserId());
    }

    @Override
    public List<User> findUsersByFederationLink(String realmId, String federationLink) {
        if (federationLink == null) {
            return null;
        }

        List<String> userIds = userDao.findUsers(realmId, FEDERATION_LINK, federationLink).all().stream()
            .map(UserSearchIndex::getUserId)
            .collect(Collectors.toList());

        return userDao.findByIds(realmId, userIds).all();
    }

    @Override
    public List<User> findUsersByIndexedAttribute(String realmId, String attributeName, String attributeValue) {
        if (attributeName == null || attributeValue == null || !attributeName.startsWith(User.INDEXED_ATTRIBUTE_PREFIX)) {
            return Collections.emptyList();
        }

        List<String> userIds = userDao.findUsers(realmId, attributeName, attributeValue).all().stream()
            .map(UserSearchIndex::getUserId)
            .collect(Collectors.toList());

        return userDao.findByIds(realmId, userIds).all();
    }

    @Override
    public void deleteUsernameSearchIndex(String realmId, User user) {
        if (user.getUsername() != null) {
            userDao.deleteIndex(realmId, USERNAME, user.getUsername(), user.getId());
        }

        if (user.getUsernameCaseInsensitive() != null) {
            userDao.deleteIndex(realmId, USERNAME_CASE_INSENSITIVE, user.getUsernameCaseInsensitive(), user.getId());
        }
    }

    @Override
    public void deleteEmailSearchIndex(String realmId, User user) {
        if (user.getEmail() != null) {
            userDao.deleteIndex(realmId, EMAIL, user.getEmail(), user.getId());
        }
    }

    @Override
    public void deleteFederationLinkSearchIndex(String realmId, User user) {
        if (user.getFederationLink() != null) {
            userDao.deleteIndex(realmId, FEDERATION_LINK, user.getFederationLink(), user.getId());
        }
    }

    @Override
    public void deleteServiceAccountLinkSearchIndex(String realmId, User user) {
        if (user.getServiceAccountClientLink() != null) {
            userDao.deleteIndex(realmId, SERVICE_ACCOUNT_LINK, user.getServiceAccountClientLink(), user.getId());
        }
    }

    @Override
    public void deleteAttributeSearchIndex(String realmId, User user, String attrName) {
        if (attrName != null && attrName.startsWith(User.INDEXED_ATTRIBUTE_PREFIX)) {
            user.getAttribute(attrName).forEach(value -> userDao.deleteIndex(realmId, attrName, value, user.getId()));
        }
    }

    @Override
    public void createOrUpdateUser(String realmId, User user) {
        userDao.insert(user);
        userDao.insert(new RealmToUserMapping(realmId, user.isServiceAccount(), user.getId()));

        if (user.getUsername() != null) {
            userDao.insertOrUpdate(new UserSearchIndex(realmId, USERNAME, user.getUsername(), user.getId()));
        }

        if (user.getUsernameCaseInsensitive() != null) {
            userDao.insertOrUpdate(new UserSearchIndex(realmId, USERNAME_CASE_INSENSITIVE, user.getUsernameCaseInsensitive(), user.getId()));
        }

        if (user.getEmail() != null) {
            userDao.insertOrUpdate(new UserSearchIndex(realmId, EMAIL, user.getEmail(), user.getId()));
        }

        if (user.getServiceAccountClientLink() != null) {
            userDao.insertOrUpdate(new UserSearchIndex(realmId, SERVICE_ACCOUNT_LINK, user.getServiceAccountClientLink(), user.getId()));
        }

        if (user.getFederationLink() != null) {
            userDao.insertOrUpdate(new UserSearchIndex(realmId, FEDERATION_LINK, user.getFederationLink(), user.getId()));
        }

        for (Map.Entry<String, List<String>> entry : user.getIndexedAttributes().entrySet()) {
            entry.getValue().forEach(value -> userDao.insertOrUpdate(new UserSearchIndex(realmId, entry.getKey(), value, user.getId())));
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

        deleteUsernameSearchIndex(realmId, user);
        deleteEmailSearchIndex(realmId, user);
        deleteServiceAccountLinkSearchIndex(realmId, user);
        deleteFederationLinkSearchIndex(realmId, user);

        for (Map.Entry<String, List<String>> entry : user.getIndexedAttributes().entrySet()) {
            entry.getValue().forEach(value -> userDao.deleteIndex(realmId, entry.getKey(), value, userId));
        }

        return true;
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
    public void createOrUpdateUserConsent(UserConsent consent) {
        userDao.insertOrUpdate(consent);
    }

    @Override
    public boolean deleteUserConsent(String realmId, String userId, String clientId) {
        return userDao.deleteUserConsent(realmId, userId, clientId);
    }

    @Override
    public boolean deleteUserConsentsByUserId(String realmId, String userId) {
        return userDao.deleteUserConsentsByUserId(realmId, userId);
    }

    @Override
    public UserConsent findUserConsent(String realmId, String userId, String clientId) {
        return userDao.findUserConsent(realmId, userId, clientId);
    }

    @Override
    public List<UserConsent> findUserConsentsByUserId(String realmId, String userId) {
        return userDao.findUserConsentsByUserId(realmId, userId).all();
    }

    @Override
    public List<UserConsent> findUserConsentsByRealmId(String realmId) {
        return userDao.findUserConsentsByRealmId(realmId).all();
    }
}
