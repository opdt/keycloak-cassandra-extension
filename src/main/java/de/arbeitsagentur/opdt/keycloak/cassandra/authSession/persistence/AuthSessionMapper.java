package de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence;

import com.datastax.oss.driver.api.mapper.annotations.DaoFactory;
import com.datastax.oss.driver.api.mapper.annotations.Mapper;

@Mapper
public interface AuthSessionMapper {
  @DaoFactory
  AuthSessionDao authSessionDao();
}
