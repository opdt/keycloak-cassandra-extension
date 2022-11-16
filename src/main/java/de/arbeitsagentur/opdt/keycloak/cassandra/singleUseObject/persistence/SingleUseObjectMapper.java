package de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence;

import com.datastax.oss.driver.api.mapper.annotations.DaoFactory;
import com.datastax.oss.driver.api.mapper.annotations.Mapper;

@Mapper
public interface SingleUseObjectMapper {
  @DaoFactory
  SingleUseObjectDao singleUseObjectDao();
}
