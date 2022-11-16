package de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.AuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSession;
import lombok.RequiredArgsConstructor;
import org.keycloak.common.util.Time;
import org.keycloak.models.map.common.TimeAdapter;

import java.util.List;

@RequiredArgsConstructor
public class CassandraAuthSessionRepository implements AuthSessionRepository {
  private final AuthSessionDao dao;

  @Override
  public void insertOrUpdate(RootAuthenticationSession session) {
    if (session.getExpiration() == null) {
      dao.insertOrUpdate(session);
    } else {
      int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(TimeAdapter.fromMilliSecondsToSeconds(session.getExpiration() - Time.currentTimeMillis()));
      dao.insertOrUpdate(session, ttl);
    }

    // Update TTL
    findAuthSessionsByParentSessionId(session.getId()).forEach(s -> insertOrUpdate(s, session));
  }

  @Override
  public void insertOrUpdate(AuthenticationSession session, RootAuthenticationSession parent) {
    if (parent.getExpiration() == null) {
      dao.insertOrUpdate(session);
    } else {
      int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(TimeAdapter.fromMilliSecondsToSeconds(parent.getExpiration() - Time.currentTimeMillis()));
      dao.insertOrUpdate(session, ttl);
    }
  }

  @Override
  public void insertOrUpdate(AuthenticationSession session) {
    dao.insertOrUpdate(session);
  }

  @Override
  public void deleteRootAuthSession(String sessionId) {
    dao.deleteRootAuthSession(sessionId);
    deleteAuthSessions(sessionId);
  }

  @Override
  public void deleteRootAuthSession(RootAuthenticationSession session) {
    dao.delete(session);
    deleteAuthSessions(session.getId());
  }

  @Override
  public void deleteAuthSession(AuthenticationSession session) {
    dao.delete(session);
  }

  @Override
  public void deleteAuthSessions(String parentSessionId) {
    dao.deleteAuthSessions(parentSessionId);
  }

  @Override
  public List<AuthenticationSession> findAuthSessionsByParentSessionId(String parentSessionId) {
    return dao.findByParentSessionId(parentSessionId).all();
  }

  @Override
  public RootAuthenticationSession findRootAuthSessionById(String id) {
    return dao.findById(id);
  }
}
