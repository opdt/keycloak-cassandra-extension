package de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.entities.LoginFailure;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CassandraLoginFailureRepository implements LoginFailureRepository {
  private final LoginFailureDao dao;

  @Override
  public void insertOrUpdate(LoginFailure loginFailure) {
    dao.insertOrUpdate(loginFailure);
  }

  @Override
  public List<LoginFailure> findLoginFailuresByUserId(String userId) {
    return dao.findByUserId(userId).all();
  }

  @Override
  public void deleteLoginFailure(LoginFailure loginFailure) {
    dao.delete(loginFailure);
  }

  @Override
  public void deleteLoginFailureByUserId(String userId) {
    dao.deleteByUserId(userId);
  }

  @Override
  public List<LoginFailure> findAllLoginFailures() {
    return dao.findAll().all();
  }
}
