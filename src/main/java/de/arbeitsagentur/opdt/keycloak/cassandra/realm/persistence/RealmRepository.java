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
package de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.ClientInitialAccess;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.Realm;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.RealmToAttributeMapping;
import org.keycloak.common.util.MultivaluedHashMap;

import java.util.List;
import java.util.Set;

public interface RealmRepository {
  Realm getRealmById(String id);

  List<Realm> getAllRealms();

  void createRealm(Realm realm);

  void deleteRealm(Realm realm);

  void insertOrUpdate(ClientInitialAccess model);

  List<ClientInitialAccess> getAllClientInitialAccessesByRealmId(String realmId);

  List<ClientInitialAccess> getAllClientInitialAccesses();

  ClientInitialAccess getClientInitialAccess(String realmId, String id);

  void deleteClientInitialAccess(ClientInitialAccess access);
  void deleteClientInitialAccess(String realmId, String id);

  // Attributes
  Set<String> findRealmIdsByAttribute(String name, String value, int firstResult, int maxResult);

  List<Realm> findRealmsByAttribute(String name, String value);

  Realm findRealmByAttribute(String name, String value);

  MultivaluedHashMap<String, String> findAllRealmAttributes(String realmId);

  RealmToAttributeMapping findRealmAttribute(String realmId, String attributeName);

  void insertOrUpdate(RealmToAttributeMapping mapping);

  boolean deleteRealmAttribute(String realmId, String attributeName);

  boolean deleteRealmAttribute(String realmId, String attributeName, String attributeValue);
}
