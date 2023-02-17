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

import com.datastax.oss.driver.api.core.cql.ResultSet;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.ClientInitialAccess;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.NameToRealm;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.Realm;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.EntityStaleException;
import lombok.RequiredArgsConstructor;
import org.keycloak.common.util.Time;
import org.keycloak.models.map.common.TimeAdapter;

import java.util.List;

@RequiredArgsConstructor
public class CassandraRealmRepository implements RealmRepository {
    private final RealmDao realmDao;

    @Override
    public void update(Realm realm) {
        if (realm.getVersion() == null) {
            realm.setVersion(1L);
            realmDao.insert(realm);
        } else {
            Long currentVersion = realm.getVersion();
            realm.incrementVersion();

            ResultSet result = realmDao.update(realm, currentVersion);

            if (!result.wasApplied()) {
                throw new EntityStaleException("Realm entity (name = '" + realm.getName() + "') couldn't be updated because its version " + currentVersion + " doesn't match the version in the database", currentVersion);
            }
        }

        realmDao.insertOrUpdate(new NameToRealm(realm.getName(), realm.getId()));
    }

    @Override
    public Realm getRealmById(String id) {
        return realmDao.getRealmById(id);
    }

    @Override
    public Realm findRealmByName(String name) {
        NameToRealm byName = realmDao.findByName(name);
        if (byName == null) {
            return null;
        }

        return getRealmById(byName.getId());
    }

    @Override
    public List<Realm> getAllRealms() {
        return realmDao.findAll().all();
    }

    @Override
    public void createRealm(Realm realm) {
        realm.setVersion(1L);
        realmDao.insert(realm);
    }

    @Override
    public void deleteRealm(Realm realm) {
        realmDao.delete(realm);
        realmDao.deleteAllClientInitialAccessModels(realm.getId());
        realmDao.deleteNameToRealm(realm.getName());
    }

    // ClientInitialAccessModel
    @Override
    public void insertOrUpdate(ClientInitialAccess model) {
        if (model.getExpiration() == null) {
            realmDao.insertOrUpdate(model);
        } else {
            int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(TimeAdapter.fromMilliSecondsToSeconds(model.getExpiration() - Time.currentTimeMillis()));
            realmDao.insertOrUpdate(model, ttl);
        }
    }

    @Override
    public List<ClientInitialAccess> getAllClientInitialAccessesByRealmId(String realmId) {
        return realmDao.getClientInitialAccesses(realmId).all();
    }

    @Override
    public List<ClientInitialAccess> getAllClientInitialAccesses() {
        return realmDao.getAllClientInitialAccesses().all();
    }

    @Override
    public void deleteClientInitialAccess(ClientInitialAccess access) {
        realmDao.deleteClientInitialAccessModel(access.getRealmId(), access.getId());
    }

    public void deleteClientInitialAccess(String realmId, String id) {
        realmDao.deleteClientInitialAccessModel(realmId, id);
    }

    @Override
    public ClientInitialAccess getClientInitialAccess(String realmId, String id) {
        return realmDao.getClientInitialAccessModelById(realmId, id);
    }
}
