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
package de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject;

import de.arbeitsagentur.opdt.keycloak.cassandra.AbstractCassandraProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.SingleUseObjectRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.entities.SingleUseObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.map.common.TimeAdapter;

import java.util.Collections;
import java.util.Map;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;

@JBossLog
@RequiredArgsConstructor
public class CassandraSingleUseObjectProvider extends AbstractCassandraProvider implements SingleUseObjectProvider {
  private final KeycloakSession session;
  private final SingleUseObjectRepository repository;

  @Override
  public void put(String key, long lifespanSeconds, Map<String, String> notes) {
    log.tracef("put(%s)%s", key, getShortStackTrace());

    SingleUseObject singleUseEntity = repository.findSingleUseObjectByKey(key);

    if (singleUseEntity != null) {
      throw new ModelDuplicateException("Single-use object entity exists: " + singleUseEntity.getKey());
    }

    singleUseEntity = SingleUseObject.builder()
        .key(key)
        .notes(notes)
        .build();

    int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(lifespanSeconds);
    repository.insertOrUpdate(singleUseEntity, ttl);
  }

  @Override
  public Map<String, String> get(String key) {
    log.tracef("get(%s)%s", key, getShortStackTrace());

    SingleUseObject singleUseEntity = repository.findSingleUseObjectByKey(key);
    if (singleUseEntity != null) {
      Map<String, String> notes = singleUseEntity.getNotes();
      return notes == null ? Collections.emptyMap() : Collections.unmodifiableMap(notes);
    }

    return null;
  }

  @Override
  public Map<String, String> remove(String key) {
    log.tracef("remove(%s)%s", key, getShortStackTrace());

    SingleUseObject singleUseEntity = repository.findSingleUseObjectByKey(key);

    if (singleUseEntity != null) {
      Map<String, String> notes = singleUseEntity.getNotes();
      if (repository.deleteSingleUseObjectByKey(key)) {
        return notes == null ? Collections.emptyMap() : Collections.unmodifiableMap(notes);
      }
    }
    // the single-use entity expired or someone else already used and deleted it
    return null;
  }

  @Override
  public boolean replace(String key, Map<String, String> notes) {
    log.tracef("replace(%s)%s", key, getShortStackTrace());

    SingleUseObject singleUseEntity = repository.findSingleUseObjectByKey(key);
    if (singleUseEntity != null) {
      singleUseEntity.setNotes(notes);
      repository.insertOrUpdate(singleUseEntity);
      return true;
    }

    return false;
  }

  @Override
  public boolean putIfAbsent(String key, long lifespanInSeconds) {
    log.tracef("putIfAbsent(%s)%s", key, getShortStackTrace());

    SingleUseObject singleUseEntity = repository.findSingleUseObjectByKey(key);
    if (singleUseEntity != null) {
      return false;
    } else {
      singleUseEntity = SingleUseObject.builder()
          .key(key)
          .build();


      int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(lifespanInSeconds);
      repository.insertOrUpdate(singleUseEntity, ttl);
      return true;
    }
  }

  @Override
  public boolean contains(String key) {
    log.tracef("contains(%s)%s", key, getShortStackTrace());

    SingleUseObject singleUseEntity = repository.findSingleUseObjectByKey(key);

    return singleUseEntity != null;
  }
}
