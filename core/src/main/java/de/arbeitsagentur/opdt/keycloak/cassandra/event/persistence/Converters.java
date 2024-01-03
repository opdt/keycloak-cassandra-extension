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
import org.keycloak.events.admin.ResourceType;
import org.keycloak.util.JsonSerialization;

public class Converters {

  public static EventEntity eventToEntity(Event ev) {
    return EventEntity.builder()
        .id(ev.getId())
        .time(ev.getTime())
        .type(ev.getType().name())
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
    Event ev = new Event();
    ev.setId(ee.getId());
    ev.setTime(ee.getTime());
    ev.setType(EventType.valueOf(ee.getType()));
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
    AdminEventEntity e = AdminEventEntity.builder()
        .id(ev.getId())
        .time(ev.getTime())
        .realmId(ev.getRealmId())
        .resourceType(ev.getResourceType().name())
        .operationType(ev.getOperationType().name())
        .authRealmId(ev.getAuthDetails().getRealmId())
        .authClientId(ev.getAuthDetails().getClientId())
        .authUserId(ev.getAuthDetails().getUserId())
        .authIpAddress(ev.getAuthDetails().getIpAddress())
        .resourcePath(ev.getResourcePath())
        .error(ev.getError())
        .build();
    if (includeRepresentation) {
      e.setRepresentation(ev.getRepresentation());
    }
    return e;
  }
  
  public static AdminEvent entityToAdminEvent(AdminEventEntity ee) {
    AdminEvent ev = new AdminEvent();
    ev.setId(ee.getId());
    ev.setTime(ee.getTime());
    ev.setRealmId(ee.getRealmId());
    ev.setResourceType(ResourceType.valueOf(ee.getResourceType()));
    ev.setOperationType(OperationType.valueOf(ee.getOperationType()));
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
  public static Map<String,String> stringToJson(String s) {
    try {
      return JsonSerialization.readValue(s, new TypeReference<HashMap<String,String>>() {}); 
    } catch (IOException e) {
      return ImmutableMap.of();
    }
  }

}
