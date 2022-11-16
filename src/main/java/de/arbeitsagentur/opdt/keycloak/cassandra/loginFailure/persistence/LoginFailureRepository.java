package de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.entities.LoginFailure;

import java.util.List;

public interface LoginFailureRepository {
  void insertOrUpdate(LoginFailure loginFailure);
  List<LoginFailure> findLoginFailuresByUserId(String userId);
  void deleteLoginFailure(LoginFailure loginFailure);
  void deleteLoginFailureByUserId(String userId);

  List<LoginFailure> findAllLoginFailures();
}
