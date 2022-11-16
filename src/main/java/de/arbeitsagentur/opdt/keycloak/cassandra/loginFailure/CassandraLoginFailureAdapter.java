package de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure;

import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.LoginFailureRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.entities.LoginFailure;
import lombok.RequiredArgsConstructor;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserLoginFailureModel;
import org.keycloak.models.map.common.TimeAdapter;

@RequiredArgsConstructor
public class CassandraLoginFailureAdapter implements UserLoginFailureModel {
  private final RealmModel realm;
  private final LoginFailure entity;
  private final LoginFailureRepository loginFailureRepository;

  @Override
  public String getId() {
    return entity.getId();
  }

  @Override
  public String getUserId() {
    return entity.getUserId();
  }

  @Override
  public int getFailedLoginNotBefore() {
    Long failedLoginNotBefore = entity.getFailedLoginNotBefore();
    return failedLoginNotBefore == null ? 0 : TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(failedLoginNotBefore);
  }

  @Override
  public void setFailedLoginNotBefore(int notBefore) {
    entity.setFailedLoginNotBefore(TimeAdapter.fromIntegerWithTimeInSecondsToLongWithTimeAsInSeconds(notBefore));
    loginFailureRepository.insertOrUpdate(entity);
  }

  @Override
  public int getNumFailures() {
    Integer numFailures = entity.getNumFailures();
    return numFailures == null ? 0 : numFailures;
  }

  @Override
  public void incrementFailures() {
    entity.setNumFailures(getNumFailures() + 1);
    loginFailureRepository.insertOrUpdate(entity);
  }

  @Override
  public void clearFailures() {
    entity.setFailedLoginNotBefore(null);
    entity.setNumFailures(null);
    entity.setLastFailure(null);
    entity.setLastIpFailure(null);
    loginFailureRepository.insertOrUpdate(entity);
  }

  @Override
  public long getLastFailure() {
    Long lastFailure = entity.getLastFailure();
    return lastFailure == null ? 0l : lastFailure;
  }

  @Override
  public void setLastFailure(long lastFailure) {
    entity.setLastFailure(lastFailure);
    loginFailureRepository.insertOrUpdate(entity);
  }

  @Override
  public String getLastIPFailure() {
    return entity.getLastIpFailure();
  }

  @Override
  public void setLastIPFailure(String ip) {
    entity.setLastIpFailure(ip);
    loginFailureRepository.insertOrUpdate(entity);
  }
}
