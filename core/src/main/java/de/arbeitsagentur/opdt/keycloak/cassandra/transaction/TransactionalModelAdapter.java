/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
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

package de.arbeitsagentur.opdt.keycloak.cassandra.transaction;

import de.arbeitsagentur.opdt.keycloak.cassandra.AttributeTypes;
import java.util.*;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(of = "entity")
public abstract class TransactionalModelAdapter<T extends TransactionalEntity>
    implements CassandraModelTransaction {
  public static final String ENTITY_VERSION =
      AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX + "entityVersion";
  public static final String ENTITY_VERSION_READONLY =
      AttributeTypes.READONLY_ATTRIBUTE_PREFIX + "entityVersion";
  private boolean updated = false;
  private boolean deleted = false;

  private final List<Runnable> postUpdateTasks = new ArrayList<>();

  protected T entity;

  public TransactionalModelAdapter(T entity) {
    this.entity = entity;
  }

  public String getId() {
    return entity.getId();
  }

  public void markUpdated() {
    updated = true;
  }

  public void markUpdated(Runnable postUpdateTask) {
    updated = true;
    postUpdateTasks.add(postUpdateTask);
  }

  public void addPostUpdateTask(Runnable postUpdateTask) {
    postUpdateTasks.add(postUpdateTask);
  }

  public void markDeleted() {
    deleted = true;
  }

  public void setAttribute(String name, List<String> values) {
    if (name == null
        || values == null
        || name.startsWith(AttributeTypes.READONLY_ATTRIBUTE_PREFIX)) {
      return;
    }

    if (ENTITY_VERSION.equals(name)) {
      entity.setVersion(Long.parseLong(values.get(0)));
    } else {
      entity.getAttributes().put(name, values);
    }

    markUpdated();
  }

  public void setSingleAttribute(String name, String value) {
    if (value != null) {
      setAttribute(name, List.of(value));
    }
  }

  public void setAttribute(String name, String value) {
    if (value != null) {
      setAttribute(name, List.of(value));
    }
  }

  public void removeAttribute(String name) {
    if (name == null) {
      return;
    }

    entity.getAttributes().remove(name);
    markUpdated();
  }

  public List<String> getAttributeValues(String name) {
    if (ENTITY_VERSION.equals(name) || ENTITY_VERSION_READONLY.equals(name)) {
      return List.of(String.valueOf(entity.getVersion()));
    }

    List<String> values = entity.getAttributes().get(name);
    return values == null
        ? Collections.emptyList()
        : values.stream().filter(v -> v != null && !v.isEmpty()).collect(Collectors.toList());
  }

  public String getAttribute(String name) {
    if (ENTITY_VERSION.equals(name) || ENTITY_VERSION_READONLY.equals(name)) {
      return String.valueOf(entity.getVersion());
    }
    List<String> values = entity.getAttributes().get(name);
    return values == null || values.isEmpty() || values.iterator().next().isEmpty()
        ? null
        : values.iterator().next();
  }

  public Map<String, String> getAttributeFirstValues() {
    Map<String, String> attributes =
        entity.getAttributes().entrySet().stream()
            .filter(e -> !e.getKey().startsWith(AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX))
            .filter(
                e ->
                    e.getValue() != null
                        && !e.getValue().isEmpty()
                        && !e.getValue().iterator().next().isEmpty())
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().iterator().next()));

    if (entity.getVersion() != null) {
      attributes.put(ENTITY_VERSION_READONLY, String.valueOf(entity.getVersion()));
    }

    return attributes;
  }

  public Map<String, List<String>> getAllAttributes() {
    Map<String, List<String>> attributes =
        entity.getAttributes().entrySet().stream()
            .filter(e -> !e.getKey().startsWith(AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX))
            .filter(
                e ->
                    e.getValue() != null
                        && !e.getValue().isEmpty()
                        && !e.getValue().iterator().next().isEmpty())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (entity.getVersion() != null) {
      attributes.put(ENTITY_VERSION_READONLY, List.of(String.valueOf(entity.getVersion())));
    }

    return attributes;
  }

  @Override
  public void commit() {
    if (updated && !deleted) {
      flushChanges();

      postUpdateTasks.forEach(Runnable::run);
      postUpdateTasks.clear();
      updated = false;
    }
  }

  protected abstract void flushChanges();
}
