package de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence;

import com.datastax.oss.driver.api.mapper.annotations.DaoFactory;
import com.datastax.oss.driver.api.mapper.annotations.Mapper;

@Mapper
public interface LoginFailureMapper {
  @DaoFactory
  LoginFailureDao loginFailureDao();
}
