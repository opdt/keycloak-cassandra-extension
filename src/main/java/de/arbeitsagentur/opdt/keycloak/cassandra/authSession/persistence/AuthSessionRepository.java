package de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.AuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSession;

import java.util.List;

public interface AuthSessionRepository {
  void insertOrUpdate(RootAuthenticationSession session);

  void insertOrUpdate(AuthenticationSession session, RootAuthenticationSession parent);

  void insertOrUpdate(AuthenticationSession session);

  void deleteRootAuthSession(String sessionId);
  void deleteRootAuthSession(RootAuthenticationSession session);

  void deleteAuthSession(AuthenticationSession session);

  void deleteAuthSessions(String parentSessionId);

  List<AuthenticationSession> findAuthSessionsByParentSessionId(String parentSessionId);

  RootAuthenticationSession findRootAuthSessionById(String id);
}
