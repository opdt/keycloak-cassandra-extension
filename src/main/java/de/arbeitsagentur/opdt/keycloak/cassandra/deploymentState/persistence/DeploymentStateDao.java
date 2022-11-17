package de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.Dao;
import com.datastax.oss.driver.api.mapper.annotations.Select;
import com.datastax.oss.driver.api.mapper.annotations.Update;
import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.entities.DeploymentState;

@Dao
public interface DeploymentStateDao {
  @Update
  void insertOrUpdate(DeploymentState deploymentState);

  @Select(customWhereClause = "id = :id")
  DeploymentState findById(String id);

  @Select
  PagingIterable<DeploymentState> findAll();
}
