package de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence;

import com.datastax.oss.driver.api.mapper.annotations.DaoFactory;
import com.datastax.oss.driver.api.mapper.annotations.Mapper;

@Mapper
public interface EventMapper {
    @DaoFactory
    EventDao eventDao();
}
