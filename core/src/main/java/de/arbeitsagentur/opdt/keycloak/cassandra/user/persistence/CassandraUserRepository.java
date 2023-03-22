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

import de.arbeitsagentur.opdt.keycloak.cassandra.AttributeTypes;
import de.arbeitsagentur.opdt.keycloak.cassandra.StreamExtensions;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.*;
import org.keycloak.common.util.Time;
import org.keycloak.models.map.common.TimeAdapter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CassandraUserRepository extends TransactionalRepository<User, UserDao> implements UserRepository {
    private static final String USERNAME = "username";
    private static final String USERNAME_CASE_INSENSITIVE = "usernameCaseInsensitive";
    private static final String EMAIL = "email";
    private static final String SERVICE_ACCOUNT_LINK = "serviceAccountLink";
    private static final String FEDERATION_LINK = "federationLink";

    public CassandraUserRepository(UserDao dao) {
        super(dao);
    }

    @Override
    public List<User> findAllUsers() {
        return dao.findAll()
            .all();
    }

    @Override
    public User findUserById(String realmId, String id) {
        return dao.findById(realmId, id);
    }

    @Override
    public User findUserByEmail(String realmId, String email) {
        if (email == null) {
            return null;
        }

        UserSearchIndex user = dao.findUsers(realmId, EMAIL, email)
            .all()
            .stream()
            .findFirst()
            .orElse(null);
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

        UserSearchIndex user = dao.findUsers(realmId, USERNAME, username)
            .all()
            .stream()
            .findFirst()
            .orElse(null);
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

        UserSearchIndex user = dao.findUsers(realmId, USERNAME_CASE_INSENSITIVE, username)
            .all()
            .stream()
            .findFirst()
            .orElse(null);

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

        UserSearchIndex user = dao.findUsers(realmId, SERVICE_ACCOUNT_LINK, serviceAccountLink)
            .all()
            .stream()
            .findFirst()
            .orElse(null);

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

        List<String> userIds = dao.findUsers(realmId, FEDERATION_LINK, federationLink)
            .all()
            .stream()
            .map(UserSearchIndex::getUserId)
            .collect(Collectors.toList());

        return dao.findByIds(realmId, userIds)
            .all();
    }

    @Override
    public List<User> findUsersByIndexedAttribute(String realmId, String attributeName, String attributeValue) {
        if (attributeName == null || attributeValue == null || !attributeName.startsWith(AttributeTypes.INDEXED_ATTRIBUTE_PREFIX)) {
            return Collections.emptyList();
        }

        List<String> userIds = dao.findUsers(realmId, attributeName, attributeValue)
            .all()
            .stream()
            .map(UserSearchIndex::getUserId)
            .collect(Collectors.toList());

        return dao.findByIds(realmId, userIds)
            .all();
    }

    @Override
    public void deleteUsernameSearchIndex(String realmId, User user) {
        if (user.getUsername() != null) {
            dao.deleteIndex(realmId, USERNAME, user.getUsername(), user.getId());
        }

        if (user.getUsernameCaseInsensitive() != null) {
            dao.deleteIndex(realmId, USERNAME_CASE_INSENSITIVE, user.getUsernameCaseInsensitive(), user.getId());
        }
    }

    @Override
    public void deleteEmailSearchIndex(String realmId, User user) {
        if (user.getEmail() != null) {
            dao.deleteIndex(realmId, EMAIL, user.getEmail(), user.getId());
        }
    }

    @Override
    public void deleteFederationLinkSearchIndex(String realmId, User user) {
        if (user.getFederationLink() != null) {
            dao.deleteIndex(realmId, FEDERATION_LINK, user.getFederationLink(), user.getId());
        }
    }

    @Override
    public void deleteServiceAccountLinkSearchIndex(String realmId, User user) {
        if (user.getServiceAccountClientLink() != null) {
            dao.deleteIndex(realmId, SERVICE_ACCOUNT_LINK, user.getServiceAccountClientLink(), user.getId());
        }
    }

    @Override
    public void deleteAttributeSearchIndex(String realmId, User user, String attrName) {
        if (attrName != null && attrName.startsWith(AttributeTypes.INDEXED_ATTRIBUTE_PREFIX)) {
            user.getAttribute(attrName)
                .forEach(value -> dao.deleteIndex(realmId, attrName, value, user.getId()));
        }
    }

    @Override
    public void insertOrUpdate(User user) {
        Integer ttl = getUserTtl(user);
        super.insertOrUpdate(user, ttl);

        insertRealmToUserMapping(user.getRealmId(), user);

        if (user.getUsername() != null) {
            insertOrUpdateSearchIndex(user.getRealmId(), USERNAME, user.getUsername(), user);
        }

        if (user.getUsernameCaseInsensitive() != null) {
            insertOrUpdateSearchIndex(user.getRealmId(), USERNAME_CASE_INSENSITIVE, user.getUsernameCaseInsensitive(), user);
        }

        if (user.getEmail() != null) {
            insertOrUpdateSearchIndex(user.getRealmId(), EMAIL, user.getEmail(), user);
        }

        if (user.getServiceAccountClientLink() != null) {
            insertOrUpdateSearchIndex(user.getRealmId(), SERVICE_ACCOUNT_LINK, user.getServiceAccountClientLink(), user);
        }

        if (user.getFederationLink() != null) {
            insertOrUpdateSearchIndex(user.getRealmId(), FEDERATION_LINK, user.getFederationLink(), user);
        }

        for (Map.Entry<String, List<String>> entry : user.getIndexedAttributes()
            .entrySet()) {
            entry.getValue()
                .forEach(value -> insertOrUpdateSearchIndex(user.getRealmId(), entry.getKey(), value, user));
        }
    }

    @Override
    public boolean deleteUser(String realmId, String userId) {
        User user = findUserById(realmId, userId);

        if (user == null) {
            return false;
        }

        dao.delete(user);
        dao.deleteRealmToUserMapping(realmId, user.isServiceAccount(), user.getId());

        deleteUsernameSearchIndex(realmId, user);
        deleteEmailSearchIndex(realmId, user);
        deleteServiceAccountLinkSearchIndex(realmId, user);
        deleteFederationLinkSearchIndex(realmId, user);

        for (Map.Entry<String, List<String>> entry : user.getIndexedAttributes()
            .entrySet()) {
            entry.getValue()
                .forEach(value -> dao.deleteIndex(realmId, entry.getKey(), value, userId));
        }

        return true;
    }

    @Override
    public void makeUserServiceAccount(User user, String realmId) {
        user.setServiceAccount(true);
        super.insertOrUpdate(user, getUserTtl(user));

        dao.deleteRealmToUserMapping(realmId, false, user.getId());
        insertRealmToUserMapping(realmId, user);

        insertOrUpdateSearchIndex(realmId, SERVICE_ACCOUNT_LINK, user.getServiceAccountClientLink(), user);
    }

    @Override
    public FederatedIdentity findFederatedIdentity(String userId, String identityProvider) {
        return dao.findFederatedIdentity(userId, identityProvider);
    }

    @Override
    public FederatedIdentity findFederatedIdentityByBrokerUserId(String brokerUserId, String identityProvider) {
        FederatedIdentityToUserMapping identityToUserMapping = dao.findFederatedIdentityByBrokerUserId(brokerUserId, identityProvider);

        if (identityToUserMapping == null) {
            return null;
        }

        return findFederatedIdentity(identityToUserMapping.getUserId(), identityProvider);
    }

    @Override
    public List<FederatedIdentity> findFederatedIdentities(String userId) {
        return dao.findFederatedIdentities(userId)
            .all();
    }

    @Override
    public void createOrUpdateFederatedIdentity(FederatedIdentity federatedIdentity) {
        User user = findUserById(federatedIdentity.getRealmId(), federatedIdentity.getUserId());
        Integer ttl = getUserTtl(user);

        FederatedIdentityToUserMapping identityToUserMapping = new FederatedIdentityToUserMapping(federatedIdentity.getBrokerUserId(), federatedIdentity.getIdentityProvider(), federatedIdentity.getUserId());
        if (ttl == null) {
            dao.update(federatedIdentity);
            dao.update(identityToUserMapping);
        } else {
            dao.update(federatedIdentity, ttl);
            dao.update(identityToUserMapping, ttl);
        }
    }

    @Override
    public boolean deleteFederatedIdentity(String userId, String identityProvider) {
        FederatedIdentity federatedIdentity = findFederatedIdentity(userId, identityProvider);

        if (federatedIdentity == null) {
            return false;
        }

        FederatedIdentityToUserMapping identityToUserMapping = dao.findFederatedIdentityByBrokerUserId(federatedIdentity.getBrokerUserId(), identityProvider);

        dao.delete(federatedIdentity);
        dao.delete(identityToUserMapping);
        return true;
    }

    @Override
    public Set<String> findUserIdsByRealmId(String realmId, int first, int max) {
        return StreamExtensions.paginated(dao.findUsersByRealmId(realmId), first, max)
            .map(RealmToUserMapping::getUserId)
            .collect(Collectors.toSet());
    }

    @Override
    public long countUsersByRealmId(String realmId, boolean includeServiceAccounts) {
        if (includeServiceAccounts) {
            return dao.countAllUsersByRealmId(realmId);
        } else {
            return dao.countNonServiceAccountUsersByRealmId(realmId);
        }
    }

    @Override
    public void createOrUpdateUserConsent(UserConsent consent) {
        User user = findUserById(consent.getRealmId(), consent.getUserId());
        Integer ttl = getUserTtl(user);

        if(ttl == null) {
            dao.insertOrUpdate(consent);
        } else {
            dao.insertOrUpdate(consent, ttl);
        }
    }

    @Override
    public boolean deleteUserConsent(String realmId, String userId, String clientId) {
        return dao.deleteUserConsent(realmId, userId, clientId);
    }

    @Override
    public boolean deleteUserConsentsByUserId(String realmId, String userId) {
        return dao.deleteUserConsentsByUserId(realmId, userId);
    }

    @Override
    public UserConsent findUserConsent(String realmId, String userId, String clientId) {
        return dao.findUserConsent(realmId, userId, clientId);
    }

    @Override
    public List<UserConsent> findUserConsentsByUserId(String realmId, String userId) {
        return dao.findUserConsentsByUserId(realmId, userId)
            .all();
    }

    @Override
    public List<UserConsent> findUserConsentsByRealmId(String realmId) {
        return dao.findUserConsentsByRealmId(realmId)
            .all();
    }


    private Integer getUserTtl(User user) {
        return user.getExpiration() == null ? null : TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(TimeAdapter.fromMilliSecondsToSeconds(user.getExpiration() - Time.currentTimeMillis()));
    }

    private void insertOrUpdateSearchIndex(String realmId, String name, String value, User user) {
        Integer ttl = getUserTtl(user);

        if (ttl != null && ttl <= 0) {
            return;
        }

        if (ttl == null) {
            dao.insertOrUpdate(new UserSearchIndex(realmId, name, value, user.getId()));
        } else {
            dao.insertOrUpdate(new UserSearchIndex(realmId, name, value, user.getId()), ttl);
        }
    }

    private void insertRealmToUserMapping(String realmId, User user) {
        Integer ttl = getUserTtl(user);

        if (ttl != null && ttl <= 0) {
            return;
        }


        if (ttl == null) {
            dao.insert(new RealmToUserMapping(realmId, user.isServiceAccount(), user.getId()));
        } else {
            dao.insert(new RealmToUserMapping(realmId, user.isServiceAccount(), user.getId()), ttl);
        }
    }
}
