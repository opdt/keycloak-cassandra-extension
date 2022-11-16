package de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.Dao;
import com.datastax.oss.driver.api.mapper.annotations.Delete;
import com.datastax.oss.driver.api.mapper.annotations.Select;
import com.datastax.oss.driver.api.mapper.annotations.Update;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.entities.LoginFailure;

@Dao
public interface LoginFailureDao {
  @Update
  void insertOrUpdate(LoginFailure loginFailure);

  @Select(customWhereClause = "user_id = :userId")
  PagingIterable<LoginFailure> findByUserId(String userId);

  @Select
  PagingIterable<LoginFailure> findAll();

  @Delete
  void delete(LoginFailure loginFailure);

  @Delete(entityClass = LoginFailure.class)
  void deleteByUserId(String userId);
}
