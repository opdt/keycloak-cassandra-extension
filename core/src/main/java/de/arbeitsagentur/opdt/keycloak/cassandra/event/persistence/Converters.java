/*
 * Copyright 2024 Phase Two, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.AdminEventEntity;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.EventEntity;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.util.JsonSerialization;

public class Converters {

  public static EventEntity eventToEntity(Event ev) {
    String type = ev.getType() != null ? ev.getType().name() : null;
    return EventEntity.builder()
        .id(ev.getId())
        .time(ev.getTime())
        .type(type)
        .realmId(ev.getRealmId())
        .clientId(ev.getClientId())
        .userId(ev.getUserId())
        .sessionId(ev.getSessionId())
        .ipAddress(ev.getIpAddress())
        .error(ev.getError())
        .time(ev.getTime())
        .detailsJsonLongValue(jsonToString(ev.getDetails()))
        .build();
  }

  public static Event entityToEvent(EventEntity ee) {
    EventType eventType = ee.getType() != null ? EventType.valueOf(ee.getType()) : null;
    Event ev = new Event();
    ev.setId(ee.getId());
    ev.setTime(ee.getTime());
    ev.setType(eventType);
    ev.setRealmId(ee.getRealmId());
    ev.setClientId(ee.getClientId());
    ev.setUserId(ee.getUserId());
    ev.setSessionId(ee.getSessionId());
    ev.setIpAddress(ee.getIpAddress());
    ev.setError(ee.getError());
    ev.setTime(ee.getTime());
    ev.setDetails(stringToJson(ee.getDetailsJsonLongValue()));
    return ev;
  }

  public static AdminEventEntity adminEventToEntity(AdminEvent ev, boolean includeRepresentation) {
    String resourceType = ev.getResourceType() != null ? ev.getResourceType().name() : null;
    String operationType = ev.getOperationType() != null ? ev.getOperationType().name() : null;
    AdminEventEntity e =
        AdminEventEntity.builder()
            .id(ev.getId())
            .time(ev.getTime())
            .realmId(ev.getRealmId())
            .resourceType(resourceType)
            .operationType(operationType)
            .resourcePath(ev.getResourcePath())
            .error(ev.getError())
            .build();
    if (ev.getAuthDetails() != null) {
      e.setAuthRealmId(ev.getAuthDetails().getRealmId());
      e.setAuthClientId(ev.getAuthDetails().getClientId());
      e.setAuthUserId(ev.getAuthDetails().getUserId());
      e.setAuthIpAddress(ev.getAuthDetails().getIpAddress());
    }
    if (includeRepresentation) {
      e.setRepresentation(ev.getRepresentation());
    }
    return e;
  }

  public static AdminEvent entityToAdminEvent(AdminEventEntity ee) {
    OperationType operationType =
        ee.getOperationType() != null ? OperationType.valueOf(ee.getOperationType()) : null;
    AdminEvent ev = new AdminEvent();
    ev.setId(ee.getId());
    ev.setTime(ee.getTime());
    ev.setRealmId(ee.getRealmId());
    ev.setResourceTypeAsString(ee.getResourceType());
    ev.setOperationType(operationType);
    ev.setResourcePath(ee.getResourcePath());
    ev.setRepresentation(ee.getRepresentation());
    ev.setError(ee.getError());
    AuthDetails de = new AuthDetails();
    de.setRealmId(ee.getAuthRealmId());
    de.setClientId(ee.getAuthClientId());
    de.setUserId(ee.getAuthUserId());
    de.setIpAddress(ee.getAuthIpAddress());
    ev.setAuthDetails(de);
    return ev;
  }

  public static String jsonToString(Object o) {
    try {
      return JsonSerialization.writeValueAsString(o);
    } catch (IOException e) {
      return "";
    }
  }

  @SuppressWarnings("unchecked")
  public static Map<String, String> stringToJson(String s) {
    try {
      return JsonSerialization.readValue(s, new TypeReference<HashMap<String, String>>() {});
    } catch (IOException e) {
      return ImmutableMap.of();
    }
  }
}
