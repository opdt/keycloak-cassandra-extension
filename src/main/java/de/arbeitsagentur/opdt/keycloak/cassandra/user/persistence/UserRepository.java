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
import org.keycloak.common.util.MultivaluedHashMap;

import java.util.List;
import java.util.Set;

public interface UserRepository {
  User findUserById(String realmId, String id);

  Set<String> findUserIdsByAttribute(
      String name, String value, int firstResult, int maxResult);

  List<User> findUsersByAttribute(String realmId, String name, String value);

  User findUserByAttribute(String realmId, String name, String value);

  MultivaluedHashMap<String, String> findAllUserAttributes(String userId);

  UserToAttributeMapping findUserAttribute(String userId, String attributeName);

  void updateAttribute(UserToAttributeMapping UserAttributeMapping);

  void deleteAttribute(String userId, String attributeName);

  void addRequiredAction(UserRequiredAction requiredAction);

  void deleteRequiredAction(String userId, String requiredAction);

  List<UserRequiredAction> findAllRequiredActions(String userId);

  void createOrUpdateUser(String realmId, User user);

  boolean deleteUser(String realmId, String userId);

  void deleteAllAttributes(String userId);

  void deleteAllRequiredActions(String userId);

  void makeUserServiceAccount(User user, String realmId);

  FederatedIdentity findFederatedIdentity(String userId, String identityProvider);

  FederatedIdentity findFederatedIdentityByBrokerUserId(
      String brokerUserId, String identityProvider);

  List<FederatedIdentity> findFederatedIdentities(String userId);

  void createOrUpdateFederatedIdentity(FederatedIdentity federatedIdentity);

  boolean deleteFederatedIdentity(String userId, String identityProvider);

  void createOrUpdateCredential(Credential credential);

  List<Credential> findCredentials(String userId);

  Credential findCredential(String userId, String credId);

  boolean deleteCredential(String userId, String credId);

  Set<String> findUserIdsByRealmId(String realmId, int first, int max);

  long countUsersByRealmId(String realmId, boolean includeServiceAccounts);

  Set<UserRealmRoleMapping> getRealmRolesByUserId(String userId);

  Set<UserClientRoleMapping> getAllClientRoleMappingsByUserId(String userId);

  void removeRoleMapping(String userId, String roleId);

  void removeClientRoleMapping(String userId, String clientId, String roleId);

  void addRealmRoleMapping(UserRealmRoleMapping roleMapping);

  void addClientRoleMapping(UserClientRoleMapping roleMapping);
}
