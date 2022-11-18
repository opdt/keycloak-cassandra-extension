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

import de.arbeitsagentur.opdt.keycloak.cassandra.StreamExtensions;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.*;
import lombok.RequiredArgsConstructor;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.common.util.Time;
import org.keycloak.models.map.common.TimeAdapter;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class CassandraRealmRepository implements RealmRepository {
  private final RealmDao realmDao;

  @Override
  public Realm getRealmById(String id) {
    return realmDao.getRealmById(id);
  }

  @Override
  public List<Realm> getAllRealms() {
    return realmDao.findAll().all();
  }

  @Override
  public void createRealm(Realm realm) {
    realmDao.insert(realm);
  }

  @Override
  public void deleteRealm(Realm realm) {
    realmDao.delete(realm);

    // ClientInitialAccessModels
    realmDao.deleteAllClientInitialAccessModels(realm.getId());

    // Attributes
    for (RealmToAttributeMapping attribute : realmDao.findAllAttributes(realm.getId())) {
      for (String attributeValue : attribute.getAttributeValues()) {
        realmDao.deleteAttributeToRealmMapping(attribute.getAttributeName(), attributeValue, realm.getId());
      }
    }

    realmDao.deleteAllRealmToAttributeMappings(realm.getId());
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

  // Attributes
  @Override
  public Set<String> findRealmIdsByAttribute(String name, String value, int firstResult, int maxResult) {
    return StreamExtensions.paginated(realmDao.findByAttribute(name, value), firstResult, maxResult)
        .map(AttributeToRealmMapping::getRealmId)
        .collect(Collectors.toSet());
  }

  @Override
  public List<Realm> findRealmsByAttribute(String name, String value) {
    return realmDao.findByAttribute(name, value).all().stream()
        .map(AttributeToRealmMapping::getRealmId)
        .flatMap(id -> Optional.ofNullable(getRealmById(id)).stream())
        .collect(Collectors.toList());
  }

  @Override
  public Realm findRealmByAttribute(String name, String value) {
    List<Realm> realms = findRealmsByAttribute(name, value);

    if (realms.size() > 1) {
      throw new IllegalStateException("Found more than one realm with attributeName " + name + " and value " + value);
    }

    if (realms.isEmpty()) {
      return null;
    }

    return realms.get(0);
  }

  @Override
  public MultivaluedHashMap<String, String> findAllRealmAttributes(String realmId) {
    List<RealmToAttributeMapping> attributeMappings = realmDao.findAllAttributes(realmId).all();
    MultivaluedHashMap<String, String> result = new MultivaluedHashMap<>();

    attributeMappings.forEach(mapping -> result.addAll(mapping.getAttributeName(), mapping.getAttributeValues()));

    return result;
  }

  @Override
  public RealmToAttributeMapping findRealmAttribute(String realmId, String attributeName) {
    return realmDao.findAttribute(realmId, attributeName);
  }

  @Override
  public void insertOrUpdate(RealmToAttributeMapping mapping) {
    RealmToAttributeMapping oldAttribute = realmDao.findAttribute(mapping.getRealmId(), mapping.getAttributeName());
    realmDao.insert(mapping);

    if (oldAttribute != null) {
      // Alte AttributeToRealmMappings löschen, da die Values als Teil des PartitionKey nicht
      // geändert werden können
      oldAttribute
          .getAttributeValues()
          .forEach(value -> realmDao.deleteAttributeToRealmMapping(oldAttribute.getAttributeName(), value, oldAttribute.getRealmId()));
    }

    mapping
        .getAttributeValues()
        .forEach(
            value -> {
              AttributeToRealmMapping attributeToRealmMapping =
                  new AttributeToRealmMapping(
                      mapping.getAttributeName(),
                      value,
                      mapping.getRealmId());
              realmDao.insert(attributeToRealmMapping);
            });
  }

  @Override
  public boolean deleteRealmAttribute(String realmId, String attributeName) {
    RealmToAttributeMapping attribute = findRealmAttribute(realmId, attributeName);

    if (attribute == null) {
      return false;
    }

    // Beide Mapping-Tabellen beachten!
    realmDao.deleteAttribute(realmId, attributeName);
    attribute
        .getAttributeValues()
        .forEach(value -> realmDao.deleteAttributeToRealmMapping(attributeName, value, realmId));
    return true;
  }

  @Override
  public boolean deleteRealmAttribute(String realmId, String attributeName, String attributeValue) {
    AttributeToRealmMapping attribute = realmDao.findByAttribute(attributeName, attributeValue).all().stream()
        .filter(a -> a.getRealmId().equals(realmId))
        .findFirst()
        .orElse(null);

    if (attribute == null) {
      return false;
    }

    // Beide Mapping-Tabellen beachten!
    realmDao.deleteAttributeToRealmMapping(attributeName, attributeValue, realmId);
    RealmToAttributeMapping realmToAttributeMapping = findRealmAttribute(realmId, attributeName);
    realmToAttributeMapping.getAttributeValues().remove(attributeValue);

    if(realmToAttributeMapping.getAttributeValues().isEmpty()) {
      realmDao.deleteAttribute(realmId, attributeName);
    } else {
      insertOrUpdate(realmToAttributeMapping);
    }

    return true;
  }
}
