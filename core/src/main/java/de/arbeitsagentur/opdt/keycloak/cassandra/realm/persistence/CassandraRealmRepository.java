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
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.NameToRealm;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.Realm;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalRepository;
import de.arbeitsagentur.opdt.keycloak.common.TimeAdapter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.keycloak.common.util.Time;

@RequiredArgsConstructor
public class CassandraRealmRepository extends TransactionalRepository implements RealmRepository {
    private final RealmDao dao;

    public void insertOrUpdate(Realm realm) {
        super.insertOrUpdateLwt(dao, realm);
        dao.insertOrUpdate(new NameToRealm(realm.getName(), realm.getId()));
    }

    @Override
    public Realm getRealmById(String id) {
        return dao.getRealmById(id);
    }

    @Override
    public Realm findRealmByName(String name) {
        NameToRealm byName = dao.findByName(name);
        if (byName == null) {
            return null;
        }

        return getRealmById(byName.getId());
    }

    @Override
    public List<Realm> getAllRealms() {
        return dao.findAll().all();
    }

    @Override
    public void createRealm(Realm realm) {
        realm.setVersion(1L);
        dao.insertLwt(realm);
        dao.insertOrUpdate(new NameToRealm(realm.getName(), realm.getId()));
    }

    @Override
    public void deleteRealm(Realm realm) {
        dao.deleteLwt(realm);
        dao.deleteAllClientInitialAccessModels(realm.getId());
        dao.deleteNameToRealm(realm.getName());
    }

    @Override
    public void deleteNameToRealm(String name) {
        dao.deleteNameToRealm(name);
    }

    // ClientInitialAccessModel
    @Override
    public void insertOrUpdate(ClientInitialAccess model) {
        if (model.getExpiration() == null) {
            dao.insertOrUpdate(model);
        } else {
            int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(
                    TimeAdapter.fromMilliSecondsToSeconds(model.getExpiration() - Time.currentTimeMillis()));
            dao.insertOrUpdate(model, ttl);
        }
    }

    @Override
    public List<ClientInitialAccess> getAllClientInitialAccessesByRealmId(String realmId) {
        return dao.getClientInitialAccesses(realmId).all();
    }

    @Override
    public List<ClientInitialAccess> getAllClientInitialAccesses() {
        return dao.getAllClientInitialAccesses().all();
    }

    @Override
    public void deleteClientInitialAccess(ClientInitialAccess access) {
        dao.deleteClientInitialAccessModel(access.getRealmId(), access.getId());
    }

    public void deleteClientInitialAccess(String realmId, String id) {
        dao.deleteClientInitialAccessModel(realmId, id);
    }

    @Override
    public ClientInitialAccess getClientInitialAccess(String realmId, String id) {
        return dao.getClientInitialAccessModelById(realmId, id);
    }
}
