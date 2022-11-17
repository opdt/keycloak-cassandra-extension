package de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence;

import com.datastax.oss.driver.api.mapper.annotations.DaoFactory;
import com.datastax.oss.driver.api.mapper.annotations.Mapper;

@Mapper
public interface DeploymentStateMapper {
  @DaoFactory
  DeploymentStateDao deploymentStateDao();
}
