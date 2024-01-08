/*
 * Copyright 2024 Phase Two, Inc.
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

package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

import com.google.common.collect.ImmutableList;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.CassandraEventStoreProvider;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.models.RealmModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.events.Event;
import org.keycloak.events.EventQuery;
import org.keycloak.events.EventType;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.representations.idm.RealmRepresentation;
import lombok.extern.jbosslog.JBossLog;

/**
 * Adapted from testsuite/integration-arquillian/tests/base/src/test/java/org/keycloak/testsuite/events/EventStoreProviderTest.java
 */
@JBossLog
public class EventStoreTest extends KeycloakModelTest {
  private String realmId;
  private String realmId2;

  static void log(String tmpl, Object... args) {
    System.err.println(String.format(tmpl, args));
  }
  
  @Override
  public void createEnvironment(KeycloakSession s) {
    RealmModel realm = s.realms().createRealm("test-id-1", "test1");
    this.realmId = realm.getId();
    realm = s.realms().createRealm("test-id-2", "test2");
    this.realmId2 = realm.getId();
  }

  @Override
  public void cleanEnvironment(KeycloakSession s) {
    s.realms().removeRealm(realmId);
    s.realms().removeRealm(realmId2);
  }

  @Test
  public void query() throws Exception {
    long oldest = System.currentTimeMillis() - 30000;
    long newest = System.currentTimeMillis() + 30000;

    inComittedTransaction(session -> {
        EventStoreProvider events = session.getProvider(EventStoreProvider.class);
        assertThat(events, instanceOf(CassandraEventStoreProvider.class));
        events.onEvent(create(EventType.LOGIN, realmId, "clientId", "userId", "127.0.0.1", "error"));
        events.onEvent(create(newest, EventType.REGISTER, realmId, "clientId", "userId", "127.0.0.1", "error"));
        events.onEvent(create(newest, EventType.REGISTER, realmId, "clientId", "userId2", "127.0.0.1", "error"));
        events.onEvent(create(EventType.LOGIN, realmId2, "clientId", "userId", "127.0.0.1", "error"));
        events.onEvent(create(oldest, EventType.LOGIN, realmId, "clientId2", "userId", "127.0.0.1", "error"));
        events.onEvent(create(EventType.LOGIN, realmId, "clientId", "userId2", "127.0.0.1", "error"));
      });
    
    inComittedTransaction(session -> {
        EventStoreProvider events = session.getProvider(EventStoreProvider.class);

        Assert.assertEquals(4, queryEvents(events, realmId, null, "clientId", null, null, null, null, null, null).size());
        Assert.assertEquals(5, queryEvents(events, realmId, null, null, null, null, null, null, null, null).size());
        Assert.assertEquals(3, queryEvents(events, realmId, toList(EventType.LOGIN), null, null, null, null, null, null, null).size());


        List<Event> evs = queryEvents(events, realmId2, toList(EventType.LOGIN), null, null, null, null, null, null, null);
        for (Event e : evs) {
          System.err.println(String.format("event %s %s %s %s %d", e.getId(), e.getRealmId(), e.getClientId(), e.getType().name(), e.getTime()));
        }
        
        
        Assert.assertEquals(1, queryEvents(events, realmId2, toList(EventType.LOGIN), null, null, null, null, null, null, null).size());
        Assert.assertEquals(5, queryEvents(events, realmId, toList(EventType.LOGIN, EventType.REGISTER), null, null, null, null, null, null, null).size());
        Assert.assertEquals(1, queryEvents(events, realmId2, toList(EventType.LOGIN, EventType.REGISTER), null, null, null, null, null, null, null).size());
        Assert.assertEquals(3, queryEvents(events, realmId, null, null, "userId", null, null, null, null, null).size());

        Assert.assertEquals(1, queryEvents(events, realmId, toList(EventType.REGISTER), null, "userId", null, null, null, null, null).size());

        Assert.assertEquals(2, queryEvents(events, realmId, null, null, null, null, null, null, null, 2).size());
        Assert.assertEquals(1, queryEvents(events, realmId, null, null, null, null, null, null, 4, null).size());
        
        Assert.assertEquals(newest, queryEvents(events, realmId, null, null, null, null, null, null, null, 1).get(0).getTime());
        Assert.assertEquals(oldest, queryEvents(events, realmId, null, null, null, null, null, null, 4, 1).get(0).getTime());

      });
    
    inComittedTransaction(session -> {
        EventStoreProvider events = session.getProvider(EventStoreProvider.class);

        events.clear(session.realms().getRealm(realmId));
        events.clear(session.realms().getRealm(realmId2));
      });
    
    inComittedTransaction(session -> {
        EventStoreProvider events = session.getProvider(EventStoreProvider.class);

        Assert.assertEquals(0, queryEvents(events, realmId, null, null, null, null, null, null, null, null).size());
      });
    
    String d04 = "2015-03-04";
    String d05 = "2015-03-05";
    String d06 = "2015-03-06";
    String d07 = "2015-03-07";
    
    String d01 = "2015-03-01";
    String d03 = "2015-03-03";
    String d08 = "2015-03-08";
    String d10 = "2015-03-10";
    
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    final Date date04 = formatter.parse(d04);
    final Date date05 = formatter.parse(d05);
    final Date date06 = formatter.parse(d06);
    final Date date07 = formatter.parse(d07);
    
    inComittedTransaction(session -> {
        EventStoreProvider events = session.getProvider(EventStoreProvider.class);

        events.onEvent(create(date04, EventType.LOGIN, realmId, "clientId", "userId", "127.0.0.1", "error"));
        events.onEvent(create(date04, EventType.LOGIN, realmId, "clientId", "userId", "127.0.0.1", "error"));
        events.onEvent(create(date05, EventType.REGISTER, realmId, "clientId", "userId", "127.0.0.1", "error"));
        events.onEvent(create(date05, EventType.REGISTER, realmId, "clientId", "userId", "127.0.0.1", "error"));
        events.onEvent(create(date06, EventType.CODE_TO_TOKEN, realmId, "clientId", "userId2", "127.0.0.1", "error"));
        events.onEvent(create(date06, EventType.LOGOUT, realmId, "clientId", "userId2", "127.0.0.1", "error"));
        events.onEvent(create(date07, EventType.UPDATE_PROFILE, realmId2, "clientId2", "userId2", "127.0.0.1", "error"));
        events.onEvent(create(date07, EventType.UPDATE_EMAIL, realmId2, "clientId2", "userId2", "127.0.0.1", "error"));
      });

    inComittedTransaction(session -> {
        EventStoreProvider events = session.getProvider(EventStoreProvider.class);

        Assert.assertEquals(6, queryEvents(events, realmId, null, "clientId", null, null, null, null, null, null).size());
        Assert.assertEquals(2, queryEvents(events, realmId2, null, "clientId2", null, null, null, null, null, null).size());

        Assert.assertEquals(6, queryEvents(events, realmId, null, null, null, null, null, null, null, null).size());
        Assert.assertEquals(2, queryEvents(events, realmId2, null, null, null, null, null, null, null, null).size());

        Assert.assertEquals(4, queryEvents(events, realmId, null, null, "userId", null, null, null, null, null).size());
        Assert.assertEquals(2, queryEvents(events, realmId, null, null, "userId2", null, null, null, null, null).size());
        Assert.assertEquals(2, queryEvents(events, realmId2, null, null, "userId2", null, null, null, null, null).size());

        Assert.assertEquals(2, queryEvents(events, realmId, toList(EventType.LOGIN), null, null, null, null, null, null, null).size());
        Assert.assertEquals(2, queryEvents(events, realmId, toList(EventType.REGISTER), null, null, null, null, null, null, null).size());
        Assert.assertEquals(4, queryEvents(events, realmId, toList(EventType.LOGIN, EventType.REGISTER), null, null, null, null, null, null, null).size());
        Assert.assertEquals(1, queryEvents(events, realmId, toList(EventType.CODE_TO_TOKEN), null, null, null, null, null, null, null).size());
        Assert.assertEquals(1, queryEvents(events, realmId, toList(EventType.LOGOUT), null, null, null, null, null, null, null).size());
        Assert.assertEquals(1, queryEvents(events, realmId2, toList(EventType.UPDATE_PROFILE), null, null, null, null, null, null, null).size());
        Assert.assertEquals(1, queryEvents(events, realmId2, toList(EventType.UPDATE_EMAIL), null, null, null, null, null, null, null).size());

        Assert.assertEquals(6, queryEvents(events, realmId, null, null, null, d04, null, null, null, null).size());
        Assert.assertEquals(6, queryEvents(events, realmId, null, null, null, null, d07, null, null, null).size());

        Assert.assertEquals(2, queryEvents(events, realmId, null, null, null, d06, null, null, null, null).size());
        Assert.assertEquals(4, queryEvents(events, realmId, null, null, null, null, d05, null, null, null).size());

        Assert.assertEquals(0, queryEvents(events, realmId2, null, null, null, d08, null, null, null, null).size());
        Assert.assertEquals(0, queryEvents(events, realmId2, null, null, null, null, d03, null, null, null).size());

        Assert.assertEquals(6, queryEvents(events, realmId, null, null, null, d04, d07, null, null, null).size());
        Assert.assertEquals(2, queryEvents(events, realmId2, null, null, null, d04, d07, null, null, null).size());
        Assert.assertEquals(4, queryEvents(events, realmId, null, null, null, d05, d07, null, null, null).size());
        Assert.assertEquals(2, queryEvents(events, realmId2, null, null, null, d05, d07, null, null, null).size());
        Assert.assertEquals(4, queryEvents(events, realmId, null, null, null, d04, d05, null, null, null).size());
        Assert.assertEquals(2, queryEvents(events, realmId, null, null, null, d06, d07, null, null, null).size());
        Assert.assertEquals(2, queryEvents(events, realmId2, null, null, null, d06, d07, null, null, null).size());

        Assert.assertEquals(0, queryEvents(events, realmId, null, null, null, d01, d03, null, null, null).size());
        Assert.assertEquals(0, queryEvents(events, realmId2, null, null, null, d01, d03, null, null, null).size());
        Assert.assertEquals(0, queryEvents(events, realmId, null, null, null, d08, d10, null, null, null).size());
        Assert.assertEquals(0, queryEvents(events, realmId2, null, null, null, d08, d10, null, null, null).size());
      });
  }

  @Test
  public void clear() {
    inComittedTransaction(session -> {
        EventStoreProvider events = session.getProvider(EventStoreProvider.class);
        events.onEvent(create(System.currentTimeMillis() - 30000, EventType.LOGIN, realmId, "clientId", "userId", "127.0.0.1", "error"));
        events.onEvent(create(System.currentTimeMillis() - 20000, EventType.LOGIN, realmId, "clientId", "userId", "127.0.0.1", "error"));
        events.onEvent(create(System.currentTimeMillis(), EventType.LOGIN, realmId, "clientId", "userId", "127.0.0.1", "error"));
        events.onEvent(create(System.currentTimeMillis(), EventType.LOGIN, realmId, "clientId", "userId", "127.0.0.1", "error"));
        events.onEvent(create(System.currentTimeMillis() - 30000, EventType.LOGIN, realmId2, "clientId", "userId", "127.0.0.1", "error"));
      });

    inComittedTransaction(session -> {
        EventStoreProvider events = session.getProvider(EventStoreProvider.class);
        events.clear(session.realms().getRealm(realmId));
      });

    inComittedTransaction(session -> {
        EventStoreProvider events = session.getProvider(EventStoreProvider.class);

        Assert.assertEquals(0, queryEvents(events, realmId, null, null, null, null, null, null, null, null).size());
        Assert.assertEquals(1, queryEvents(events, realmId2, null, null, null, null, null, null, null, null).size());
      });
  }
  
  private Event create(EventType event, String realmId, String clientId, String userId, String ipAddress, String error) {
    return create(System.currentTimeMillis(), event, realmId, clientId, userId, ipAddress, error);
  }

  private Event create(Date date, EventType event, String realmId, String clientId, String userId, String ipAddress, String error) {
    return create(date.getTime(), event, realmId, clientId, userId, ipAddress, error);
  }
  
  private Event create(long time, EventType event, String realmId, String clientId, String userId, String ipAddress, String error) {
    Event e = new Event();
    e.setId(KeycloakModelUtils.generateId());
    e.setTime(time);
    e.setType(event);
    e.setRealmId(realmId);
    e.setClientId(clientId);
    e.setUserId(userId);
    e.setIpAddress(ipAddress);
    e.setError(error);
    
    Map<String, String> details = new HashMap<String, String>();
    details.put("key1", "value1");
    details.put("key2", "value2");
    
    e.setDetails(details);
    
    return e;
  }

  private List<Event> queryEvents(EventStoreProvider events, String realmId, List<String> types, String client, String user, String dateFrom, String dateTo, String ipAddress, Integer firstResult, Integer maxResults) {
    if (types == null) types = ImmutableList.of();
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    Date fromDate = null, toDate = null;
    try {
      if (dateFrom != null) fromDate = formatter.parse(dateFrom);
      if (dateTo != null) toDate = formatter.parse(dateTo);
    } catch (ParseException e) {
      //ignore
    }
    
    EventQuery query = events.createQuery()
                       .realm(realmId)
                       .type(types.stream().map(EventType::valueOf).collect(Collectors.toList()).toArray(new EventType[0]))
                       .client(client)
                       .user(user)
                       .fromDate(fromDate)
                       .toDate(toDate)
                       .ipAddress(ipAddress);
    if (firstResult != null) query.firstResult(firstResult);
    if (maxResults != null) query.maxResults(firstResult);
    return query.getResultStream()
        .collect(Collectors.toList());
  }

  private List<String> toList(Enum... enumTypes) {
    List<String> enumList = new ArrayList<>();
    for (Enum type : enumTypes) {
      enumList.add(type.toString());
    }
    return enumList;
  }

}
