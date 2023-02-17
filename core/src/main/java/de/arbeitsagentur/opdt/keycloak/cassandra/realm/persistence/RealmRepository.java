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

import java.util.List;

public interface RealmRepository {
    void update(Realm realm);

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

    Realm findRealmByName(String name);
}
