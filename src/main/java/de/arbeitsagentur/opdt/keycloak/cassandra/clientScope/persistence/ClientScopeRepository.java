package de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScope;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopeToAttributeMapping;

import java.util.List;

public interface ClientScopeRepository {
    void create(ClientScope clientScope);

    ClientScope getClientScopeById(String id);

    List<ClientScope> findAllClientScopes();

    List<ClientScope> findAllClientScopesByAttribute(String attributeName, String attributeValue);

    void insertOrUpdate(ClientScopeToAttributeMapping mapping);

    void remove(ClientScope clientScope);

    void deleteClientScopeAttribute(String clientScopeId, String name);

    ClientScopeToAttributeMapping findClientScopeAttribute(String clientScopeId, String name);

    List<ClientScopeToAttributeMapping> findAllClientScopeAttributes(String clientScopeId);
}
