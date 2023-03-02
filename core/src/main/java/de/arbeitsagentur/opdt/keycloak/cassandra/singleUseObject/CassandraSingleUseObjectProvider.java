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

import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.SingleUseObjectRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.entities.SingleUseObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.map.common.TimeAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;

@JBossLog
@RequiredArgsConstructor
public class CassandraSingleUseObjectProvider implements SingleUseObjectProvider {
    private static final String EMPTY_NOTE = "internal.emptyNote";

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
            .notes(getInternalNotes(notes))
            .build();

        int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(lifespanSeconds);
        repository.insertOrUpdate(singleUseEntity, ttl);
    }

    @Override
    public Map<String, String> get(String key) {
        log.tracef("get(%s)%s", key, getShortStackTrace());

        SingleUseObject singleUseEntity = repository.findSingleUseObjectByKey(key);
        if (singleUseEntity != null) {
            return getExternalNotes(singleUseEntity.getNotes());
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
                return getExternalNotes(notes);
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
            singleUseEntity.setNotes(getInternalNotes(notes));
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
                .notes(getInternalNotes(null))
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

    @Override
    public void close() {
        // Nothing to do
    }

    private Map<String, String> getInternalNotes(Map<String, String> notes) {
        Map<String, String> result = notes == null ? new HashMap<>() : notes.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (result.isEmpty()) {
            result.put(EMPTY_NOTE, EMPTY_NOTE);
        }

        return result;
    }

    private Map<String, String> getExternalNotes(Map<String, String> notes) {
        Map<String, String> result = notes == null ? new HashMap<>() : new HashMap<>(notes);

        result.remove(EMPTY_NOTE);

        return result;
    }
}
