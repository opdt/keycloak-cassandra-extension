package de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence;

import com.datastax.oss.driver.api.mapper.annotations.DaoFactory;
import com.datastax.oss.driver.api.mapper.annotations.Mapper;

@Mapper
public interface GroupMapper {
  @DaoFactory
  GroupDao groupDao();
}
