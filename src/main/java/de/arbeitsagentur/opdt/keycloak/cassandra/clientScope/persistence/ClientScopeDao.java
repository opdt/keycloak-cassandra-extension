package de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence;

import com.datastax.dse.driver.api.core.graph.GraphResultSet;
import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.AttributeToClientScopeMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScope;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopeToAttributeMapping;

@Dao
public interface ClientScopeDao {
    @Insert
    void insert(ClientScope clientScope);

    @Select(customWhereClause = "id = :id")
    ClientScope getClientScopeById(String id);

    @Select
    PagingIterable<ClientScope> findAllClientScopes();

    @Update
    void insertOrUpdate(ClientScopeToAttributeMapping attributeMapping);

    @Insert
    void insert(AttributeToClientScopeMapping attributeToClientMapping);

    @Select(customWhereClause = "attribute_name = :attributeName AND attribute_value = :attributeValue")
    PagingIterable<AttributeToClientScopeMapping> findAllClientScopeMappingsByAttribute(String attributeName, String attributeValue);

    @Select(customWhereClause = "client_scope_id = :clientScopeId AND attribute_name = :attributeName")
    ClientScopeToAttributeMapping findClientScopeAttribute(String clientScopeId, String attributeName);

    @Select(customWhereClause = "client_scope_id = :clientScopeId")
    PagingIterable<ClientScopeToAttributeMapping> findAllClientScopeAttributes(String clientScopeId);

    @Delete(entityClass = ClientScopeToAttributeMapping.class)
    void deleteClientScopeAttribute(String clientScopeId, String attributeName);

    @Delete
    void deleteAttributeToClientScopeMapping(AttributeToClientScopeMapping attributeToClientMapping);

    @Delete
    void delete(ClientScope clientScope);
}
