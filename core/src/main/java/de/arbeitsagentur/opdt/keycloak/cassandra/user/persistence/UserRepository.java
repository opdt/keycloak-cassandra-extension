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

import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.*;

import java.util.List;
import java.util.Set;

public interface UserRepository {
  List<User> findAllUsers();

  User findUserById(String realmId, String id);

  User findUserByEmail(String realmId, String email);

  User findUserByUsername(String realmId, String username);

  User findUserByUsernameCaseInsensitive(String realmId, String username);

  User findUserByServiceAccountLink(String realmId, String serviceAccountLink);

  List<User> findUsersByFederationLink(String realmId, String federationLink);

  void deleteUsernameSearchIndex(String realmId, User user);

  void deleteEmailSearchIndex(String realmId, User user);

  void deleteFederationLinkSearchIndex(String realmId, User user);

  void deleteServiceAccountLinkSearchIndex(String realmId, User user);

  void createOrUpdateUser(String realmId, User user);

  boolean deleteUser(String realmId, String userId);

  void makeUserServiceAccount(User user, String realmId);

  FederatedIdentity findFederatedIdentity(String userId, String identityProvider);

  FederatedIdentity findFederatedIdentityByBrokerUserId(
      String brokerUserId, String identityProvider);

  List<FederatedIdentity> findFederatedIdentities(String userId);

  void createOrUpdateFederatedIdentity(FederatedIdentity federatedIdentity);

  boolean deleteFederatedIdentity(String userId, String identityProvider);

  Set<String> findUserIdsByRealmId(String realmId, int first, int max);

  long countUsersByRealmId(String realmId, boolean includeServiceAccounts);
}
