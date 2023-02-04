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

import de.arbeitsagentur.opdt.keycloak.cassandra.AbstractCassandraProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.cache.ThreadLocalCache;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.FederatedIdentity;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.User;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.utils.KeycloakModelUtils.isUsernameCaseSensitive;

@JBossLog
public class CassandraUserProvider extends AbstractCassandraProvider implements UserProvider {

    private final KeycloakSession session;
    private final UserRepository userRepository;

    public CassandraUserProvider(KeycloakSession session, UserRepository userRepository) {
        this.session = session;
        this.userRepository = userRepository;
    }

    private Function<User, UserModel> entityToAdapterFunc(RealmModel realm) {
        return origEntity -> origEntity == null ? null : new CassandraUserAdapter(realm, userRepository, origEntity) {

            @Override
            public boolean checkEmailUniqueness(RealmModel realm, String email) {
                return getUserByEmail(realm, email) != null;
            }

            @Override
            public boolean checkUsernameUniqueness(RealmModel realm, String username) {
                return getUserByUsername(realm, username) != null;
            }


            @Override
            public SubjectCredentialManager credentialManager() {
                return new CassandraCredentialManager(session, realm, userRepository, this, origEntity);
            }
        };
    }

    @Override
    public UserModel addUser(RealmModel realm, String username) {
        return addUser(realm, null, username, true, true);
    }

    @Override
    public UserModel addUser(RealmModel realm, String id, String username, boolean addDefaultRoles, boolean addDefaultRequiredActions) {
        log.debugv("addUser realm={0} id={1} username={2}", realm, id, username);

        UserModel existingUser = getUserByUsername(realm, username);

        if (existingUser != null) {
            throw new ModelDuplicateException("User with username '" + username + "' in realm " + realm.getName() + " already exists");
        }

        if (id != null && userRepository.findUserById(realm.getId(), id) != null) {
            throw new ModelDuplicateException("User exists: " + id);
        }

        User user = User.builder()
            .id(id == null ? KeycloakModelUtils.generateId() : id)
            .realmId(realm.getId())
            .createdTimestamp(Instant.now())
            .build();

        userRepository.createOrUpdateUser(realm.getId(), user);

        final UserModel userModel = entityToAdapterFunc(realm).apply(user);
        userModel.setUsername(username);

        if (addDefaultRoles) {
            if (realm.getDefaultRole() != null) {
                userModel.grantRole(realm.getDefaultRole());
            }

            // No need to check if user has group as it's new user
            realm.getDefaultGroupsStream().forEach(userModel::joinGroup);
        }

        if (addDefaultRequiredActions) {
            realm.getRequiredActionProvidersStream()
                .filter(RequiredActionProviderModel::isEnabled)
                .filter(RequiredActionProviderModel::isDefaultAction)
                .map(RequiredActionProviderModel::getAlias)
                .forEach(userModel::addRequiredAction);
        }

        return userModel;
    }

    @Override
    public void setNotBeforeForUser(RealmModel realm, UserModel user, int notBefore) {
        UserModel newlyLoaded = getByIdOrThrow(realm, user);
        newlyLoaded.setSingleAttribute(CassandraUserAdapter.NOT_BEFORE, String.valueOf(notBefore));
    }

    @Override
    public int getNotBeforeOfUser(RealmModel realm, UserModel user) {
        UserModel newlyLoaded = getByIdOrThrow(realm, user);
        String notBeforeAttribute = newlyLoaded.getFirstAttribute(CassandraUserAdapter.NOT_BEFORE);
        if (notBeforeAttribute == null) {
            return 0;
        }
        return Integer.parseInt(notBeforeAttribute);
    }

    @Override
    public UserModel getServiceAccount(ClientModel client) {
        User user = userRepository.findUserByServiceAccountLink(client.getRealm().getId(), client.getId());
        return entityToAdapterFunc(client.getRealm()).apply(user);
    }


    @Override
    public void removeImportedUsers(RealmModel realm, String storageProviderId) {
        log.tracef("removeImportedUsers(%s, %s)%s", realm, storageProviderId, getShortStackTrace());
        List<User> users = userRepository.findUsersByFederationLink(realm.getId(), storageProviderId);
        users.forEach(u -> userRepository.deleteUser(realm.getId(), u.getId()));
    }

    @Override
    public void unlinkUsers(RealmModel realm, String storageProviderId) {
        log.tracef("unlinkUsers(%s, %s)%s", realm, storageProviderId, getShortStackTrace());
        List<User> users = userRepository.findUsersByFederationLink(realm.getId(), storageProviderId);
        users.forEach(u -> userRepository.deleteFederationLinkSearchIndex(realm.getId(), u));
    }

    @Override
    public void addConsent(RealmModel realm, String userId, UserConsentModel consent) {
        // TODO: Implement
    }

    @Override
    public UserConsentModel getConsentByClient(RealmModel realm, String userId, String clientInternalId) {
        // TODO: Implement
        return null;
    }

    @Override
    public Stream<UserConsentModel> getConsentsStream(RealmModel realm, String userId) {
        // TODO: Implement
        return null;
    }

    @Override
    public void updateConsent(RealmModel realm, String userId, UserConsentModel consent) {
        // TODO: Implement
    }

    @Override
    public boolean revokeConsentForClient(RealmModel realm, String userId, String clientInternalId) {
        // TODO: Implement
        return false;
    }

    @Override
    public void addFederatedIdentity(RealmModel realm, UserModel user, FederatedIdentityModel socialLink) {
        updateFederatedIdentity(realm, user, socialLink);
    }

    private FederatedIdentity toFederatedIdentity(RealmModel realm, UserModel user, FederatedIdentityModel model) {
        return FederatedIdentity.builder()
            .userId(user.getId())
            .brokerUserId(model.getUserId())
            .brokerUserName(model.getUserName())
            .realmId(realm.getId())
            .identityProvider(model.getIdentityProvider())
            .token(model.getToken())
            .build();
    }

    private FederatedIdentityModel toModel(FederatedIdentity federatedIdentity) {
        if (federatedIdentity == null) {
            return null;
        }

        return new FederatedIdentityModel(federatedIdentity.getIdentityProvider(),
            federatedIdentity.getBrokerUserId(), federatedIdentity.getBrokerUserName(), federatedIdentity.getToken());
    }

    @Override
    public boolean removeFederatedIdentity(RealmModel realm, UserModel user, String socialProvider) {
        return userRepository.deleteFederatedIdentity(user.getId(), socialProvider);
    }

    @Override
    public void updateFederatedIdentity(RealmModel realm, UserModel federatedUser, FederatedIdentityModel federatedIdentityModel) {
        if (federatedUser == null || federatedUser.getId() == null) {
            return;
        }

        userRepository.createOrUpdateFederatedIdentity(toFederatedIdentity(realm, federatedUser, federatedIdentityModel));
    }

    @Override
    public Stream<FederatedIdentityModel> getFederatedIdentitiesStream(RealmModel realm, UserModel user) {
        return userRepository.findFederatedIdentities(user.getId()).stream().map(this::toModel);
    }

    @Override
    public FederatedIdentityModel getFederatedIdentity(RealmModel realm, UserModel user, String socialProvider) {
        FederatedIdentity federatedIdentity = userRepository.findFederatedIdentity(user.getId(), socialProvider);
        return toModel(federatedIdentity);
    }

    @Override
    public UserModel getUserByFederatedIdentity(RealmModel realm, FederatedIdentityModel socialLink) {
        FederatedIdentity federatedIdentity = userRepository.findFederatedIdentityByBrokerUserId(socialLink.getUserId(), socialLink.getIdentityProvider());

        if (federatedIdentity == null) {
            return null;
        }

        User userById = userRepository.findUserById(realm.getId(), federatedIdentity.getUserId());
        return entityToAdapterFunc(realm).apply(userById);
    }

    @Override
    public void grantToAllUsers(RealmModel realm, RoleModel role) {
        List<User> users = userRepository.findAllUsers().stream()
            .filter(u -> u.getRealmId().equals(realm.getId()))
            .collect(Collectors.toList());
        for (User user : users) {
            if (role.isClientRole()) {
                Set<String> clientRoles = user.getClientRoles().getOrDefault(role.getContainerId(), new HashSet<>());
                clientRoles.add(role.getId());
            } else {
                user.getRealmRoles().add(role.getId());
            }

            userRepository.createOrUpdateUser(realm.getId(), user);
        }
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        log.debugv("getUserById realm={0} id={1}", realm, id);
        User userById = userRepository.findUserById(realm.getId(), id);
        return entityToAdapterFunc(realm).apply(userById);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        log.debugv("getUserByUsername realm={0} username={1}", realm, username);
        User userByUsername = isUsernameCaseSensitive(realm)
            ? userRepository.findUserByUsername(realm.getId(), username)
            : userRepository.findUserByUsernameCaseInsensitive(realm.getId(), KeycloakModelUtils.toLowerCaseSafe(username));
        return entityToAdapterFunc(realm).apply(userByUsername);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        log.debugv("getUserByEmail realm={0} email={1}", realm, email);
        User userByEmail = userRepository.findUserByEmail(realm.getId(), email);
        UserModel userModel = entityToAdapterFunc(realm).apply(userByEmail);

        if (userModel == null) {
            return null;
        }

        return entityToAdapterFunc(realm).apply(userByEmail);
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, String search, Integer firstResult, Integer maxResults) {
        log.tracef("searchForUserStream(%s, %s, %d, %d)%s", realm, search, firstResult, maxResults, getShortStackTrace());
        Map<String, String> attributes = new HashMap<>();
        attributes.put(UserModel.SEARCH, search);
        return searchForUserStream(realm, attributes, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params, Integer firstResult, Integer maxResults) {
        String searchString = params.get(UserModel.SEARCH);
        log.debugf("Search with searchString %s", searchString);

        String serviceAccountParam = params.get(UserModel.INCLUDE_SERVICE_ACCOUNT);
        Boolean includeServiceAccounts = serviceAccountParam == null
            ? null
            : Boolean.parseBoolean(serviceAccountParam);

        if (searchString == null) {
            int first = firstResult == null ? 0 : firstResult;
            int last = maxResults == null ? -1 : maxResults;
            return userRepository.findUserIdsByRealmId(realm.getId(), first, last).stream()
                .flatMap(id -> Optional.ofNullable(this.getUserById(realm, id)).stream())
                .filter(u -> includeServiceAccounts == null || includeServiceAccounts || u.getServiceAccountClientLink() == null)
                .sorted(Comparator.comparing(UserModel::getUsername));
        } else if (params.containsKey(UserModel.EMAIL)) {
            return Optional.ofNullable(userRepository.findUserByEmail(realm.getId(), params.get(UserModel.EMAIL)))
                .filter(u -> includeServiceAccounts == null || includeServiceAccounts || u.getServiceAccountClientLink() == null)
                .map(entityToAdapterFunc(realm)).stream();
        } else {
            return Optional.ofNullable(userRepository.findUserByUsernameCaseInsensitive(realm.getId(), KeycloakModelUtils.toLowerCaseSafe(searchString)))
                .filter(u -> includeServiceAccounts == null || includeServiceAccounts || u.getServiceAccountClientLink() == null)
                .map(entityToAdapterFunc(realm)).stream();
        }
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group, Integer firstResult, Integer maxResults) {
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue) {
        log.debugf("Search with attribute %s:%s", attrName, attrValue);

        if (attrName == null || attrValue == null) {
            return Stream.empty();
        }

        if (attrName.startsWith(User.INDEXED_ATTRIBUTE_PREFIX)) {
            return userRepository.findUsersByIndexedAttribute(realm.getId(), attrName, attrValue).stream()
                .map(entityToAdapterFunc(realm))
                .filter(u -> u.getServiceAccountClientLink() == null)
                .sorted(Comparator.comparing(UserModel::getUsername));
        } else {
            return userRepository.findAllUsers().stream()
                .filter(u -> u.getRealmId().equals(realm.getId()))
                .filter(u -> u.getAttribute(attrName).contains(attrValue))
                .filter(u -> u.getServiceAccountClientLink() == null)
                .map(entityToAdapterFunc(realm))
                .sorted(Comparator.comparing(UserModel::getUsername));
        }
    }

    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        log.debugv("getUsersCount realm={0} includeServiceAccount={1}", realm.getId(), includeServiceAccount);

        return (int) userRepository.countUsersByRealmId(realm.getId(), includeServiceAccount);
    }

    @Override
    public boolean removeUser(RealmModel realm, UserModel user) {
        return userRepository.deleteUser(realm.getId(), user.getId());
    }

    @Override
    public void preRemove(RealmModel realm) {
        log.tracef("preRemove[RealmModel](%s)%s", realm, getShortStackTrace());
    }

    @Override
    public void preRemove(RealmModel realm, IdentityProviderModel provider) {
        String providerAlias = provider.getAlias();
        log.tracef("preRemove[RealmModel realm, IdentityProviderModel provider](%s, %s)%s", realm, providerAlias, getShortStackTrace());

        List<User> users = userRepository.findUsersByFederationLink(realm.getId(), providerAlias);
        users.forEach(u -> userRepository.deleteFederatedIdentity(realm.getId(), providerAlias));
    }

    @Override
    public void preRemove(RealmModel realm, RoleModel role) {
        // NOOP (delete/ignore ad-hoc when read)
    }

    @Override
    public void preRemove(RealmModel realm, GroupModel group) {
        // NOOP (delete/ignore ad-hoc when read)
    }

    @Override
    public void preRemove(RealmModel realm, ClientModel client) {
        // TODO: Implement (Consent)
    }

    @Override
    public void preRemove(ProtocolMapperModel protocolMapper) {
        // NOOP
    }

    @Override
    public void preRemove(ClientScopeModel clientScope) {
        // TODO: Implement (Consent)
    }

    @Override
    public void preRemove(RealmModel realm, ComponentModel component) {
        // NOOP
    }

    private UserModel getByIdOrThrow(RealmModel realm, UserModel user) {
        UserModel userById = getUserById(realm, user.getId());
        if (userById == null) {
            throw new ModelException("Specified user doesn't exist.");
        }

        return userById;
    }

    @Override
    protected String getCacheName() {
        return ThreadLocalCache.USER_CACHE;
    }
}
