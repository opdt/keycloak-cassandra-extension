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
import lombok.extern.jbosslog.JBossLog;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.events.Event;
import org.keycloak.events.EventQuery;
import org.keycloak.events.EventStoreProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.AdminEventQuery;
import org.keycloak.events.admin.AuthDetails;
import org.keycloak.events.admin.OperationType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;

/**
 * Adapted from
 * testsuite/integration-arquillian/tests/base/src/test/java/org/keycloak/testsuite/events/AdminEventStoreProviderTest.java
 */
@JBossLog
public class AdminEventStoreTest extends KeycloakModelTest {
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
  public void query() {
    long oldest = System.currentTimeMillis() - 30000;
    long newest = System.currentTimeMillis() + 30000;

    inComittedTransaction(
        session -> {
          EventStoreProvider events = session.getProvider(EventStoreProvider.class);

          events.onEvent(create(realmId, OperationType.CREATE, realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(newest, realmId, OperationType.ACTION, realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(newest, realmId, OperationType.ACTION, realmId, "clientId", "userId2", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(realmId2, OperationType.CREATE, realmId2, "clientId", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(oldest, realmId, OperationType.CREATE, realmId, "clientId2", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(realmId, OperationType.CREATE, realmId, "clientId", "userId2", "127.0.0.1", "/admin/realms/master", "error"), false);

          Assert.assertEquals(4, getAdminEvents(events, realmId, null, null, "clientId", null, null, null, null, null, null, null).size());
          Assert.assertEquals(1, getAdminEvents(events, realmId2, null, null, "clientId", null, null, null, null, null, null, null).size());
          Assert.assertEquals(5, getAdminEvents(events, realmId, null, realmId, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(3, getAdminEvents(events, realmId, toList(OperationType.CREATE), null, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(1, getAdminEvents(events, realmId2, toList(OperationType.CREATE), null, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(5, getAdminEvents(events, realmId, toList(OperationType.CREATE, OperationType.ACTION), null, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(1, getAdminEvents(events, realmId2, toList(OperationType.CREATE, OperationType.ACTION), null, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(3, getAdminEvents(events, realmId, null, null, null, "userId", null, null, null, null, null, null).size());
          Assert.assertEquals(1, getAdminEvents(events, realmId2, null, null, null, "userId", null, null, null, null, null, null).size());
          
          Assert.assertEquals(1, getAdminEvents(events, realmId, toList(OperationType.ACTION), null, null, "userId", null, null, null, null, null, null).size());
          Assert.assertEquals(0, getAdminEvents(events, realmId2, toList(OperationType.ACTION), null, null, "userId", null, null, null, null, null, null).size());
          
          Assert.assertEquals(2, getAdminEvents(events, realmId, null, null, null, null, null, null, null, null, null, 2).size());
          Assert.assertEquals(1, getAdminEvents(events, realmId, null, null, null, null, null, null, null, null, 4, null).size());
          
          Assert.assertEquals(newest, getAdminEvents(events, realmId, null, null, null, null, null, null, null, null, null, 1).get(0).getTime());
          Assert.assertEquals(oldest, getAdminEvents(events, realmId, null, null, null, null, null, null, null, null, 4, 1).get(0).getTime());
          
          events.clearAdmin(session.realms().getRealm(realmId));
          events.clearAdmin(session.realms().getRealm(realmId2));
          
          Assert.assertEquals(0, getAdminEvents(events, realmId, null, null, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(0, getAdminEvents(events, realmId2, null, null, null, null, null, null, null, null, null, null).size());

          String d04 = "2015-03-04";
          String d05 = "2015-03-05";
          String d06 = "2015-03-06";
          String d07 = "2015-03-07";
          
          String d01 = "2015-03-01";
          String d03 = "2015-03-03";
          String d08 = "2015-03-08";
          String d10 = "2015-03-10";
          
          SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
          Date date04 = null, date05 = null, date06 = null, date07 = null;
          
          try {
            date04 = formatter.parse(d04);
            date05 = formatter.parse(d05);
            date06 = formatter.parse(d06);
            date07 = formatter.parse(d07);
          } catch (ParseException e) {
            e.printStackTrace();
          }
          
          events.onEvent(create(date04, realmId, OperationType.CREATE, realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(date04, realmId, OperationType.CREATE, realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(date05, realmId, OperationType.ACTION, realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(date05, realmId, OperationType.ACTION, realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(date06, realmId, OperationType.UPDATE, realmId, "clientId", "userId2", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(date06, realmId, OperationType.DELETE, realmId, "clientId", "userId2", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(date07, realmId2, OperationType.CREATE, realmId2, "clientId2", "userId2", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(date07, realmId2, OperationType.CREATE, realmId2, "clientId2", "userId2", "127.0.0.1", "/admin/realms/master", "error"), false);

          Assert.assertEquals(6, getAdminEvents(events, realmId, null, null, "clientId", null, null, null, null, null, null, null).size());
          Assert.assertEquals(0, getAdminEvents(events, realmId2, null, null, "clientId", null, null, null, null, null, null, null).size());
          Assert.assertEquals(0, getAdminEvents(events, realmId, null, null, "clientId2", null, null, null, null, null, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId2, null, null, "clientId2", null, null, null, null, null, null, null).size());

          Assert.assertEquals(6, getAdminEvents(events, realmId, null, realmId, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId2, null, realmId2, null, null, null, null, null, null, null, null).size());

          Assert.assertEquals(4, getAdminEvents(events, realmId, null, null, null, "userId", null, null, null, null, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId, null, null, null, "userId2", null, null, null, null, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId2, null, null, null, "userId2", null, null, null, null, null, null).size());

          Assert.assertEquals(2, getAdminEvents(events, realmId, toList(OperationType.ACTION), null, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(4, getAdminEvents(events, realmId, toList(OperationType.CREATE, OperationType.ACTION), null, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId2, toList(OperationType.CREATE, OperationType.ACTION), null, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(1, getAdminEvents(events, realmId, toList(OperationType.UPDATE), null, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(1, getAdminEvents(events, realmId, toList(OperationType.DELETE), null, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId, toList(OperationType.CREATE), null, null, null, null, null, null, null, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId2, toList(OperationType.CREATE), null, null, null, null, null, null, null, null, null).size());

          Assert.assertEquals(6, getAdminEvents(events, realmId, null, null, null, null, null, null, d04, null, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId2, null, null, null, null, null, null, d04, null, null, null).size());
          Assert.assertEquals(6, getAdminEvents(events, realmId, null, null, null, null, null, null, null, d07, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId2, null, null, null, null, null, null, null, d07, null, null).size());

          Assert.assertEquals(2, getAdminEvents(events, realmId, null, null, null, null, null, null, d06, null, null, null).size());
          Assert.assertEquals(4, getAdminEvents(events, realmId, null, null, null, null, null, null, null, d05, null, null).size());

          Assert.assertEquals(0, getAdminEvents(events, realmId2, null, null, null, null, null, null, d08, null, null, null).size());
          Assert.assertEquals(0, getAdminEvents(events, realmId2, null, null, null, null, null, null, null, d03, null, null).size());

          Assert.assertEquals(6, getAdminEvents(events, realmId, null, null, null, null, null, null, d04, d07, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId2, null, null, null, null, null, null, d04, d07, null, null).size());
          Assert.assertEquals(4, getAdminEvents(events, realmId, null, null, null, null, null, null, d05, d07, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId2, null, null, null, null, null, null, d05, d07, null, null).size());
          Assert.assertEquals(4, getAdminEvents(events, realmId, null, null, null, null, null, null, d04, d05, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId, null, null, null, null, null, null, d06, d07, null, null).size());
          Assert.assertEquals(2, getAdminEvents(events, realmId2, null, null, null, null, null, null, d06, d07, null, null).size());

          Assert.assertEquals(0, getAdminEvents(events, realmId, null, null, null, null, null, null, d01, d03, null, null).size());
          Assert.assertEquals(0, getAdminEvents(events, realmId2, null, null, null, null, null, null, d01, d03, null, null).size());
          Assert.assertEquals(0, getAdminEvents(events, realmId, null, null, null, null, null, null, d08, d10, null, null).size());
          Assert.assertEquals(0, getAdminEvents(events, realmId2, null, null, null, null, null, null, d08, d10, null, null).size());

        });
  }

  //TODO enable SASI indexes in future?
  // @Test
  // public void queryResourcePath() {
  //   long oldest = System.currentTimeMillis() - 30000;
  //   long newest = System.currentTimeMillis() + 30000;

  //   inComittedTransaction(
  //       session -> {
  //         EventStoreProvider events = session.getProvider(EventStoreProvider.class);
  //         events.onEvent(create(realmId, OperationType.CREATE, realmId, "clientId", "userId",
  //                               "127.0.0.1", "/admin/realms/master", "error"), false);
  //         events.onEvent(create(newest, realmId, OperationType.ACTION, realmId, "clientId",
  //                               "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
  //         events.onEvent(create(newest, realmId, OperationType.ACTION, realmId, "clientId",
  //                               "userId2", "127.0.0.1", "/admin/realms/master", "error"), false);
  //         events.onEvent(create(realmId2, OperationType.CREATE, realmId2, "clientId",
  //                               "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
  //         events.onEvent(create(oldest, realmId, OperationType.CREATE, realmId, "clientId2",
  //                               "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
  //         events.onEvent(create(realmId, OperationType.CREATE, realmId, "clientId",
  //                               "userId2", "127.0.0.1", "/admin/realms/master", "error"), false);

  //         Assert.assertEquals(5, getAdminEvents(events, realmId, null, null, null, null, null,
  //                                               "/admin/*", null, null, null, null).size());
  //         Assert.assertEquals(5, getAdminEvents(events, realmId, null, null, null, null, null,
  //                                               "*/realms/*", null, null, null, null).size());
  //         Assert.assertEquals(5, getAdminEvents(events, realmId, null, null, null, null, null,
  //                                               "*/master", null, null, null, null).size());
  //         Assert.assertEquals(5, getAdminEvents(events, realmId, null, null, null, null, null,
  //                                               "/admin/realms/*", null, null, null, null).size());
  //         Assert.assertEquals(5, getAdminEvents(events, realmId, null, null, null, null, null,
  //                                               "*/realms/master", null, null, null, null).size());
  //         Assert.assertEquals(5, getAdminEvents(events, realmId, null, null, null, null, null,
  //                                               "/admin/*/master", null, null, null, null).size());
  //         Assert.assertEquals(5, getAdminEvents(events, realmId, null, null, null, null, null,
  //                                               "/ad*/*/master", null, null, null, null).size());
          
  //         Assert.assertEquals(1, getAdminEvents(events, realmId2, null, null, null, null, null,
  //                                               "/admin/*", null, null, null, null).size());
  //         Assert.assertEquals(1, getAdminEvents(events, realmId2, null, null, null, null, null,
  //                                               "*/realms/*", null, null, null, null).size());
  //         Assert.assertEquals(1, getAdminEvents(events, realmId2, null, null, null, null, null,
  //                                               "*/master", null, null, null, null).size());
  //         Assert.assertEquals(1, getAdminEvents(events, realmId2, null, null, null, null, null,
  //                                               "/admin/realms/*", null, null, null, null).size());
  //         Assert.assertEquals(1, getAdminEvents(events, realmId2, null, null, null, null, null,
  //                                               "*/realms/master", null, null, null, null).size());
  //         Assert.assertEquals(1, getAdminEvents(events, realmId2, null, null, null, null, null,
  //                                               "/admin/*/master", null, null, null, null).size());
  //         Assert.assertEquals(1, getAdminEvents(events, realmId2, null, null, null, null, null,
  //                                               "/ad*/*/master", null, null, null, null).size());
  //         events.clearAdmin(session.realms().getRealm(realmId));
  //         events.clearAdmin(session.realms().getRealm(realmId2));
  //       });
  // }

  @Test
  public void clear() {
    inComittedTransaction(
        session -> {
          EventStoreProvider events = session.getProvider(EventStoreProvider.class);
          events.onEvent(create(System.currentTimeMillis() - 30000, realmId,
                                OperationType.CREATE, realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master",
                                "error"), false);
          events.onEvent(create(System.currentTimeMillis() - 20000, realmId,
                                OperationType.CREATE, realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master",
                                "error"), false);
          events.onEvent(create(System.currentTimeMillis(), realmId, OperationType.CREATE,
                                realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(System.currentTimeMillis(), realmId, OperationType.CREATE,
                                realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(System.currentTimeMillis() - 30000, realmId2,
                                OperationType.CREATE, realmId2, "clientId", "userId", "127.0.0.1", "/admin/realms/master",
                                "error"), false);

          events.clearAdmin(session.realms().getRealm(realmId));
          
          Assert.assertEquals(0, getAdminEvents(events, realmId, null, null, null, null, null,
                                                null, null, null, null, null).size());
          Assert.assertEquals(1, getAdminEvents(events, realmId2, null, null, null, null, null,
                                                null, null, null, null, null).size());

          events.clearAdmin(session.realms().getRealm(realmId2));
          Assert.assertEquals(0, getAdminEvents(events, realmId2, null, null, null, null, null,
                                                null, null, null, null, null).size());
        });
  }

  @Test
  public void clearOld() {
    inComittedTransaction(
        session -> {
          EventStoreProvider events = session.getProvider(EventStoreProvider.class);
          events.onEvent(create(System.currentTimeMillis() - 30000, realmId,
                                OperationType.CREATE, realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master",
                                "error"), false);
          events.onEvent(create(System.currentTimeMillis() - 20000, realmId,
                                OperationType.CREATE, realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master",
                                "error"), false);
          events.onEvent(create(System.currentTimeMillis(), realmId, OperationType.CREATE,
                                realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(System.currentTimeMillis(), realmId, OperationType.CREATE,
                                realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master", "error"), false);
          events.onEvent(create(System.currentTimeMillis() - 30000, realmId,
                                OperationType.CREATE, realmId, "clientId", "userId", "127.0.0.1", "/admin/realms/master",
                                "error"), false);

          events.clearAdmin(session.realms().getRealm(realmId), System.currentTimeMillis() - 10000);
          Assert.assertEquals(2, getAdminEvents(events, realmId, null, null, null, null, null,
                                                null, null, null, null, null).size());
          
          events.clearAdmin(session.realms().getRealm(realmId));
          Assert.assertEquals(0, getAdminEvents(events, realmId, null, null, null, null, null,
                                                null, null, null, null, null).size());
        });
  }

  @Test
  public void handleCustomResourceTypeEvents() {
    inComittedTransaction(
        session -> {
          EventStoreProvider events = session.getProvider(EventStoreProvider.class);
          events.onEvent(create(realmId, OperationType.CREATE, realmId, "clientId", "userId",
                                "127.0.0.1", "/admin/realms/master", "my-custom-resource", "error"), false);

          List<AdminEvent> adminEvents = getAdminEvents(events, realmId, null, null,
                                                                          "clientId", null, null, null, null, null, null, null);
          Assert.assertEquals(1, adminEvents.size());
          // CUSTOM?
          //Assert.assertEquals("my-custom-resource", adminEvents.get(0).getResourceType());

          events.clearAdmin(session.realms().getRealm(realmId));
        });
  }

  private AdminEvent create(
      String realmId,
      OperationType operation,
      String authRealmId,
      String authClientId,
      String authUserId,
      String authIpAddress,
      String resourcePath,
      String error) {
    return create(
        System.currentTimeMillis(),
        realmId,
        operation,
        authRealmId,
        authClientId,
        authUserId,
        authIpAddress,
        resourcePath,
        error);
  }

  private AdminEvent create(
      String realmId,
      OperationType operation,
      String authRealmId,
      String authClientId,
      String authUserId,
      String authIpAddress,
      String resourcePath,
      String resourceType,
      String error) {
    return create(
        System.currentTimeMillis(),
        realmId,
        operation,
        authRealmId,
        authClientId,
        authUserId,
        authIpAddress,
        resourcePath,
        resourceType,
        error);
  }

  private AdminEvent create(
      Date date,
      String realmId,
      OperationType operation,
      String authRealmId,
      String authClientId,
      String authUserId,
      String authIpAddress,
      String resourcePath,
      String error) {
    return create(
        date.getTime(),
        realmId,
        operation,
        authRealmId,
        authClientId,
        authUserId,
        authIpAddress,
        resourcePath,
        error);
  }

  private AdminEvent create(
      long time,
      String realmId,
      OperationType operation,
      String authRealmId,
      String authClientId,
      String authUserId,
      String authIpAddress,
      String resourcePath,
      String error) {
    return create(
        time,
        realmId,
        operation,
        authRealmId,
        authClientId,
        authUserId,
        authIpAddress,
        resourcePath,
        null,
        error);
  }

  private AdminEvent create(
      long time,
      String realmId,
      OperationType operation,
      String authRealmId,
      String authClientId,
      String authUserId,
      String authIpAddress,
      String resourcePath,
      String resourceType,
      String error) {
    AdminEvent e = new AdminEvent();
    e.setId(KeycloakModelUtils.generateId());
    e.setTime(time);
    e.setRealmId(realmId);
    e.setOperationType(operation);
    AuthDetails authDetails = new AuthDetails();
    authDetails.setRealmId(authRealmId);
    authDetails.setClientId(authClientId);
    authDetails.setUserId(authUserId);
    authDetails.setIpAddress(authIpAddress);
    e.setAuthDetails(authDetails);
    e.setResourcePath(resourcePath);
    e.setResourceTypeAsString(resourceType);
    e.setError(error);
    return e;
  }

  private List<AdminEvent>getAdminEvents(EventStoreProvider events,
                                    String realmId, List<String> operationTypes, String authRealm, String authClient,
                                    String authUser, String authIpAddress,
                                    String resourcePath, String dateFrom,
                                    String dateTo, Integer firstResult,
                                    Integer maxResults) {
    if (operationTypes == null) operationTypes = ImmutableList.of();
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
    Date fromDate = null, toDate = null;
    try {
      if (dateFrom != null) fromDate = formatter.parse(dateFrom);
      if (dateTo != null) toDate = formatter.parse(dateTo);
    } catch (ParseException e) {
      // ignore
    }

    AdminEventQuery query =
        events
        .createAdminQuery()
        .realm(realmId)
        .operation(
            operationTypes.stream()
            .map(OperationType::valueOf)
            .collect(Collectors.toList())
            .toArray(new OperationType[0]))
        .authRealm(authRealm)
        .authClient(authClient)
        .authUser(authUser)
        .authIpAddress(authIpAddress)
        .resourcePath(resourcePath)
        .fromTime(fromDate)
        .toTime(toDate);
    if (firstResult != null) query.firstResult(firstResult);
    if (maxResults != null) query.maxResults(maxResults);
    return query.getResultStream().collect(Collectors.toList());
  }

}
