package de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence;

import com.datastax.oss.driver.api.mapper.annotations.DaoFactory;
import com.datastax.oss.driver.api.mapper.annotations.Mapper;
import ua_parser.Client;

@Mapper
public interface ClientMapper {
    @DaoFactory
    ClientDao clientDao();
}
