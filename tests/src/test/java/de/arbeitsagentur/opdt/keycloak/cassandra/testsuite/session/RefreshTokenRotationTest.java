package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.KeycloakModelTest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import java.security.cert.X509Certificate;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.keycloak.OAuth2Constants;
import org.keycloak.common.util.Resteasy;
import org.keycloak.common.util.Time;
import org.keycloak.http.FormPartValue;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.*;

public class RefreshTokenRotationTest extends KeycloakModelTest {

  private String realmId;

  @Override
  public void createEnvironment(KeycloakSession s) {
    RealmModel realm = createRealm(s, "test");
    realm.setDefaultRole(
        s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));

    s.users().addUser(realm, "user1").setEmail("user1@localhost");
    s.clients().addClient(realm, "testClient");

    this.realmId = realm.getId();
  }

  @Override
  public void cleanEnvironment(KeycloakSession s) {
    RealmModel realm = s.realms().getRealm(realmId);
    s.sessions().removeUserSessions(realm);

    UserModel user1 = s.users().getUserByUsername(realm, "user1");

    UserManager um = new UserManager(s);
    if (user1 != null) {
      um.removeUser(realm, user1);
    }

    ClientModel testClient = s.clients().getClientByClientId(realm, "testClient");
    s.clients().removeClient(realm, testClient.getId());

    s.realms().removeRealm(realmId);
  }

  @Test
  public void testRefreshTokenRotationMultiTab() {
    withRealm(
        realmId,
        (session, realm) -> {
          realm.setSsoSessionIdleTimeout(1800);
          realm.setSsoSessionMaxLifespan(36000);
          realm.setClientSessionIdleTimeout(1000);

          realm.setAttribute("refreshTokenReuseInterval", 100);
          realm.setRefreshTokenMaxReuse(0);
          return null;
        });

    String uSId =
        withRealm(
            realmId,
            (session, realm) -> {
              UserSessionModel userSession =
                  session
                      .sessions()
                      .createUserSession(
                          realm,
                          session.users().getUserByUsername(realm, "user1"),
                          "user1",
                          "127.0.0.1",
                          "form",
                          true,
                          null,
                          null);

              ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
              session.sessions().createClientSession(realm, testClient, userSession);

              return userSession.getId();
            });

    // Refresh Tab 1
    withRealm(
        realmId,
        (session, realm) -> {
          UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);
          Resteasy.pushContext(HttpRequest.class, createHttpRequest("header.body.sig1"));

          ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
          AuthenticatedClientSessionModel clientSession =
              userSession.getAuthenticatedClientSessionByClient(testClient.getId());

          assertEquals("sig1", clientSession.getCurrentRefreshToken());
          assertEquals(0, clientSession.getCurrentRefreshTokenUseCount());

          clientSession.setCurrentRefreshTokenUseCount(1);

          return null;
        });

    // Reuse inside given interval
    withRealm(
        realmId,
        (session, realm) -> {
          UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);
          Resteasy.pushContext(HttpRequest.class, createHttpRequest("header.body.sig1"));

          ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
          AuthenticatedClientSessionModel clientSession =
              userSession.getAuthenticatedClientSessionByClient(testClient.getId());

          assertEquals("sig1", clientSession.getCurrentRefreshToken());
          assertEquals(0, clientSession.getCurrentRefreshTokenUseCount());

          clientSession.setCurrentRefreshTokenUseCount(1);

          return null;
        });

    // Reuse outside given interval
    Time.setOffset(101);
    withRealm(
        realmId,
        (session, realm) -> {
          UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);
          Resteasy.pushContext(HttpRequest.class, createHttpRequest("header.body.sig1"));

          ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
          AuthenticatedClientSessionModel clientSession =
              userSession.getAuthenticatedClientSessionByClient(testClient.getId());

          assertEquals("sig1", clientSession.getCurrentRefreshToken());
          assertEquals(1, clientSession.getCurrentRefreshTokenUseCount());

          return null;
        });

    // Refresh Tab 2
    withRealm(
        realmId,
        (session, realm) -> {
          UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);
          Resteasy.pushContext(HttpRequest.class, createHttpRequest("header.body.sig2"));

          ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
          AuthenticatedClientSessionModel clientSession =
              userSession.getAuthenticatedClientSessionByClient(testClient.getId());

          assertEquals("sig2", clientSession.getCurrentRefreshToken());
          assertEquals(0, clientSession.getCurrentRefreshTokenUseCount());

          return null;
        });
  }

  @Test
  public void testRefreshTokenRotationOldBehavior() {
    withRealm(
        realmId,
        (session, realm) -> {
          realm.setSsoSessionIdleTimeout(1800);
          realm.setSsoSessionMaxLifespan(36000);
          realm.setClientSessionIdleTimeout(1000);

          realm.setAttribute("refreshTokenReuseInterval", 100);
          realm.setRefreshTokenMaxReuse(1); // Reuse > 0 triggers old behavior
          return null;
        });

    String uSId =
        withRealm(
            realmId,
            (session, realm) -> {
              UserSessionModel userSession =
                  session
                      .sessions()
                      .createUserSession(
                          realm,
                          session.users().getUserByUsername(realm, "user1"),
                          "user1",
                          "127.0.0.1",
                          "form",
                          true,
                          null,
                          null);

              ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
              session.sessions().createClientSession(realm, testClient, userSession);

              return userSession.getId();
            });

    // Refresh 1
    withRealm(
        realmId,
        (session, realm) -> {
          UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);
          Resteasy.pushContext(HttpRequest.class, createHttpRequest("header.body.sig1"));

          ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
          AuthenticatedClientSessionModel clientSession =
              userSession.getAuthenticatedClientSessionByClient(testClient.getId());

          assertNull(clientSession.getCurrentRefreshToken());
          assertEquals(0, clientSession.getCurrentRefreshTokenUseCount());

          clientSession.setCurrentRefreshToken("currentToken");
          clientSession.setCurrentRefreshTokenUseCount(1);

          return null;
        });

    withRealm(
        realmId,
        (session, realm) -> {
          UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);
          Resteasy.pushContext(HttpRequest.class, createHttpRequest("header.body.sig1"));

          ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
          AuthenticatedClientSessionModel clientSession =
              userSession.getAuthenticatedClientSessionByClient(testClient.getId());

          assertEquals("currentToken", clientSession.getCurrentRefreshToken());
          assertEquals(1, clientSession.getCurrentRefreshTokenUseCount());

          return null;
        });

    // Refresh Tab 2
    withRealm(
        realmId,
        (session, realm) -> {
          UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);
          Resteasy.pushContext(HttpRequest.class, createHttpRequest("header.body.sig2"));

          ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
          AuthenticatedClientSessionModel clientSession =
              userSession.getAuthenticatedClientSessionByClient(testClient.getId());

          assertEquals("currentToken", clientSession.getCurrentRefreshToken());
          assertEquals(1, clientSession.getCurrentRefreshTokenUseCount());

          return null;
        });
  }

  @NotNull
  private static HttpRequest createHttpRequest(String refreshToken) {
    return new HttpRequest() {
      @Override
      public String getHttpMethod() {
        return null;
      }

      @Override
      public MultivaluedMap<String, String> getDecodedFormParameters() {
        return new MultivaluedHashMap<>(Map.of(OAuth2Constants.REFRESH_TOKEN, refreshToken));
      }

      @Override
      public MultivaluedMap<String, FormPartValue> getMultiPartFormParameters() {
        return null;
      }

      @Override
      public HttpHeaders getHttpHeaders() {
        return null;
      }

      @Override
      public X509Certificate[] getClientCertificateChain() {
        return new X509Certificate[0];
      }

      @Override
      public UriInfo getUri() {
        return null;
      }
    };
  }
}
