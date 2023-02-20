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
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.CassandraModelTransaction;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.FederatedIdentity;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.User;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.UserConsent;
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

    private final Map<String, CassandraUserAdapter> userModels = new HashMap<>();

    public CassandraUserProvider(KeycloakSession session, UserRepository userRepository) {
        this.session = session;
        this.userRepository = userRepository;
    }

    private Function<User, UserModel> entityToAdapterFunc(RealmModel realm) {
        return origEntity -> {
            if (origEntity == null) {
                return null;
            }

            CassandraUserAdapter existingModel = userModels.get(origEntity.getId());
            if (existingModel != null) {
                log.tracef("Return cached user-model for id %s", origEntity.getId());
                return existingModel;
            }
            CassandraUserAdapter adapter = new CassandraUserAdapter(realm, userRepository, origEntity) {
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

            session.getTransactionManager().enlistAfterCompletion((CassandraModelTransaction) () -> {
                log.tracef("Flush user-model with id %s", adapter.getId());
                adapter.flush();
                userModels.remove(adapter.getId());
            });
            userModels.put(adapter.getId(), adapter);
            return adapter;
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

        UserModel userModel = entityToAdapterFunc(realm).apply(user);
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

        ((CassandraUserAdapter) userModel).flush(); // initial save
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
        List<UserModel> users = userRepository.findUsersByFederationLink(realm.getId(), storageProviderId).stream()
            .map(entityToAdapterFunc(realm))
            .collect(Collectors.toList());
        users.forEach(u -> removeUser(realm, u));
    }

    @Override
    public void unlinkUsers(RealmModel realm, String storageProviderId) {
        log.tracef("unlinkUsers(%s, %s)%s", realm, storageProviderId, getShortStackTrace());
        List<User> users = userRepository.findUsersByFederationLink(realm.getId(), storageProviderId);
        users.forEach(u -> userRepository.deleteFederationLinkSearchIndex(realm.getId(), u));
    }

    @Override
    public void addConsent(RealmModel realm, String userId, UserConsentModel consent) {
        log.debugv("addConsent({0}, {1}, {2})", realm, userId, consent);
        userRepository.createOrUpdateUserConsent(fromModel(realm, userId, consent));
    }

    @Override
    public UserConsentModel getConsentByClient(RealmModel realm, String userId, String clientInternalId) {
        log.debugv("getConsentByClient({0}, {1}, {2})", realm, userId, clientInternalId);
        UserConsent userConsent = userRepository.findUserConsent(realm.getId(), userId, clientInternalId);

        return toModel(realm, userConsent);
    }

    @Override
    public Stream<UserConsentModel> getConsentsStream(RealmModel realm, String userId) {
        log.debugv("getConsentByClientStream({0}, {1})", realm, userId);

        return userRepository.findUserConsentsByUserId(realm.getId(), userId)
            .stream()
            .map(userConsent -> toModel(realm, userConsent));
    }


    @Override
    public void updateConsent(RealmModel realm, String userId, UserConsentModel consent) {
        log.debugv("updateConsent({0}, {1}, {2})", realm, userId, consent);

        UserConsent userConsent = userRepository.findUserConsent(realm.getId(), userId, consent.getClient().getId());

        userConsent.setGrantedClientScopesId(
            consent.getGrantedClientScopes().stream()
                .map(ClientScopeModel::getId)
                .collect(Collectors.toSet())
        );
        userConsent.setLastUpdatedTimestamp(Instant.now());

        userRepository.createOrUpdateUserConsent(userConsent);
    }

    @Override
    public boolean revokeConsentForClient(RealmModel realm, String userId, String clientInternalId) {
        log.debugv("revokeConsentForClient({0}, {1}, {2})", realm, userId, clientInternalId);

        return userRepository.deleteUserConsent(realm.getId(), userId, clientInternalId);
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

    private UserConsentModel toModel(RealmModel realm, UserConsent userConsent) {
        if (userConsent == null) {
            return null;
        }

        ClientModel client = realm.getClientById(userConsent.getClientId());
        if (client == null) {
            throw new ModelException("Client with id " + userConsent.getClientId() + " is not available");
        }

        UserConsentModel model = new UserConsentModel(client);
        model.setCreatedDate(userConsent.getCreatedTimestamp().toEpochMilli());
        model.setLastUpdatedDate(userConsent.getLastUpdatedTimestamp().toEpochMilli());

        Set<String> grantedClientScopesIds = userConsent.getGrantedClientScopesId();

        if (grantedClientScopesIds != null && !grantedClientScopesIds.isEmpty()) {
            grantedClientScopesIds.stream()
                .map(scopeId -> KeycloakModelUtils.findClientScopeById(realm, client, scopeId))
                .filter(Objects::nonNull)
                .forEach(model::addGrantedClientScope);
        }

        return model;
    }

    private UserConsent fromModel(RealmModel realm, String userId, UserConsentModel consentModel) {

        UserConsent userConsent = new UserConsent();
        userConsent.setRealmId(realm.getId());
        userConsent.setClientId(consentModel.getClient().getId());
        userConsent.setUserId(userId);

        consentModel.getGrantedClientScopes()
            .stream()
            .map(ClientScopeModel::getId)
            .forEach(userConsent::addGrantedClientScopesId);

        return userConsent;
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
        searchForUserStream(realm, "").forEach(u -> u.grantRole(role));
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        log.debugv("getUserById realm={0} id={1}", realm, id);
        CassandraUserAdapter existingUser = userModels.get(id);
        if(existingUser != null) {
            return existingUser;
        }

        User userById = userRepository.findUserById(realm.getId(), id);
        return entityToAdapterFunc(realm).apply(userById);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        log.debugv("getUserByUsername realm={0} username={1}", realm, username);
        return userModels.values().stream()
            .filter(model -> model.getRealm().equals(realm))
            .filter(model -> model.hasUsername(username))
            .map(model -> (UserModel) model)
            .findFirst()
            .orElseGet(() -> entityToAdapterFunc(realm).apply(isUsernameCaseSensitive(realm)
                ? userRepository.findUserByUsername(realm.getId(), username)
                : userRepository.findUserByUsernameCaseInsensitive(realm.getId(), KeycloakModelUtils.toLowerCaseSafe(username)))
            );
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        log.debugv("getUserByEmail realm={0} email={1}", realm, email);
        return userModels.values().stream()
            .filter(model -> model.getRealm().equals(realm))
            .filter(model -> model.getEmail() != null && model.getEmail().equals(email))
            .map(model -> (UserModel) model)
            .findFirst()
            .orElseGet(() -> entityToAdapterFunc(realm).apply(userRepository.findUserByEmail(realm.getId(), email)));
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

        if ((searchString == null) || searchString.isEmpty()) {
            int first = firstResult == null ? 0 : firstResult;
            int last = maxResults == null ? -1 : maxResults;
            return userRepository.findUserIdsByRealmId(realm.getId(), first, last).stream()
                .flatMap(id -> Optional.ofNullable(this.getUserById(realm, id)).stream())
                .filter(u -> includeServiceAccounts == null || includeServiceAccounts || u.getServiceAccountClientLink() == null)
                .sorted(Comparator.comparing(UserModel::getUsername));
        } else if (params.containsKey(UserModel.EMAIL)) {
            String email = params.get(UserModel.EMAIL);

            return Stream.ofNullable(getUserByEmail(realm, email))
                .filter(u -> includeServiceAccounts == null || includeServiceAccounts || u.getServiceAccountClientLink() == null);
        } else {
            return Stream.ofNullable(getUserByUsername(realm, searchString))
                .filter(u -> includeServiceAccounts == null || includeServiceAccounts || u.getServiceAccountClientLink() == null);
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
            return
                Stream.concat(
                    userModels.values().stream()
                        .filter(model -> model.getRealm().equals(realm))
                        .filter(model -> model.getAttributes().getOrDefault(attrName, Collections.emptyList()).contains(attrValue))
                        .filter(u -> u.getServiceAccountClientLink() == null),
                    userRepository.findUsersByIndexedAttribute(realm.getId(), attrName, attrValue).stream()
                        .map(entityToAdapterFunc(realm))
                        .filter(u -> u.getServiceAccountClientLink() == null)
                        .sorted(Comparator.comparing(UserModel::getUsername))).distinct();
        } else {
            return Stream.concat(
                userModels.values().stream()
                    .filter(model -> model.getRealm().equals(realm))
                    .filter(model -> model.getAttributes().getOrDefault(attrName, Collections.emptyList()).contains(attrValue))
                    .filter(u -> u.getServiceAccountClientLink() == null),
                userRepository.findAllUsers().stream()
                    .filter(u -> u.getRealmId().equals(realm.getId()))
                    .filter(u -> u.getAttribute(attrName).contains(attrValue))
                    .filter(u -> u.getServiceAccountClientLink() == null)
                    .map(entityToAdapterFunc(realm))
                    .sorted(Comparator.comparing(UserModel::getUsername))).distinct();
        }
    }

    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        log.debugv("getUsersCount realm={0} includeServiceAccount={1}", realm.getId(), includeServiceAccount);

        return (int) userRepository.countUsersByRealmId(realm.getId(), includeServiceAccount);
    }

    @Override
    public boolean removeUser(RealmModel realm, UserModel user) {
        userRepository.deleteUserConsentsByUserId(realm.getId(), user.getId());
        userModels.remove(user.getId());
        return ((CassandraUserAdapter) user).delete();
    }

    @Override
    public void preRemove(RealmModel realm) {
        log.tracef("preRemove[RealmModel](%s)%s", realm, getShortStackTrace());
        searchForUserStream(realm, "").forEach(u -> removeUser(realm, u));
        userModels.clear();
    }

    @Override
    public void preRemove(RealmModel realm, IdentityProviderModel provider) {
        String providerAlias = provider.getAlias();
        log.tracef("preRemove[RealmModel realm, IdentityProviderModel provider](%s, %s)%s", realm, providerAlias, getShortStackTrace());

        List<User> users = userRepository.findUsersByFederationLink(realm.getId(), providerAlias);
        users.forEach(u -> {
            userRepository.deleteFederatedIdentity(realm.getId(), providerAlias);
        });
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
        String clientId = client.getId();
        String realmId = realm.getId();
        log.debugv("preRemove[ClientModel]({0}, {1})", realmId, clientId);

        List<UserConsent> userConsents = userRepository.findUserConsentsByRealmId(realmId);
        if (userConsents != null && !userConsents.isEmpty()) {
            userConsents.forEach(userConsent -> {
                if (userConsent.getClientId().equals(clientId))
                    userRepository.deleteUserConsent(realmId, userConsent.getUserId(), clientId);
            });
        }
    }

    @Override
    public void preRemove(ProtocolMapperModel protocolMapper) {
        // NOOP
    }

    @Override
    public void preRemove(ClientScopeModel clientScope) {
        String clientScopeId = clientScope.getId();
        String realmId = clientScope.getRealm().getId();
        log.debugv("preRemove[ClientScopeModel]({0})", clientScopeId);

        List<UserConsent> userConsents = userRepository.findUserConsentsByRealmId(realmId);
        if (userConsents != null && !userConsents.isEmpty()) {
            userConsents.forEach(userConsent -> {
                if (userConsent.removeGrantedClientScopesId(clientScopeId)) {
                    userConsent.setLastUpdatedTimestamp(Instant.now());
                    userRepository.createOrUpdateUserConsent(userConsent);
                }
            });
        }
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
    protected List<String> getCacheNames() {
        return Arrays.asList(ThreadLocalCache.USER_CACHE, ThreadLocalCache.USER_CONSENT_CACHE);
    }
}
