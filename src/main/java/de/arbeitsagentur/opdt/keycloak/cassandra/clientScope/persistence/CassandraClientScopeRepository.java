package de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.AttributeToClientMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.AttributeToClientScopeMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScope;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopeToAttributeMapping;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class CassandraClientScopeRepository implements ClientScopeRepository {
    private final ClientScopeDao clientScopeDao;

    @Override
    public void create(ClientScope clientScope) {
        clientScopeDao.insert(clientScope);
    }

    @Override
    public ClientScope getClientScopeById(String id) {
        return clientScopeDao.getClientScopeById(id);
    }

    @Override
    public List<ClientScope> findAllClientScopes() {
        return clientScopeDao.findAllClientScopes().all();
    }

    @Override
    public List<ClientScope> findAllClientScopesByAttribute(String attributeName, String attributeValue) {
        return clientScopeDao.findAllClientScopeMappingsByAttribute(attributeName, attributeValue).all().stream()
                .map(AttributeToClientScopeMapping::getClientScopeId)
                .flatMap(id -> Optional.ofNullable(getClientScopeById(id)).stream())
                .collect(Collectors.toList());
    }

    @Override
    public void insertOrUpdate(ClientScopeToAttributeMapping attributeMapping) {
        ClientScopeToAttributeMapping oldAttribute = clientScopeDao.findClientScopeAttribute(attributeMapping.getClientScopeId(), attributeMapping.getAttributeName());
        clientScopeDao.insertOrUpdate(attributeMapping);

        if (oldAttribute != null) {
            oldAttribute
                    .getAttributeValues()
                    .forEach(value -> clientScopeDao.deleteAttributeToClientScopeMapping(new AttributeToClientScopeMapping(oldAttribute.getAttributeName(), value, oldAttribute.getClientScopeId())));
        }

        attributeMapping
                .getAttributeValues()
                .forEach(value -> {
                    AttributeToClientScopeMapping attributeToClientMapping = new AttributeToClientScopeMapping(attributeMapping.getAttributeName(), value, attributeMapping.getClientScopeId());
                    clientScopeDao.insert(attributeToClientMapping);
                });

    }

    @Override
    public void remove(ClientScope clientScope) {
        clientScopeDao.delete(clientScope);
    }

    @Override
    public void deleteClientScopeAttribute(String clientScopeId, String attributeName) {
        ClientScopeToAttributeMapping attribute = findClientScopeAttribute(clientScopeId, attributeName);

        if (attribute == null) {
            return;
        }

        clientScopeDao.deleteClientScopeAttribute(clientScopeId, attributeName);
        attribute
                .getAttributeValues()
                .forEach(value -> clientScopeDao.deleteAttributeToClientScopeMapping(new AttributeToClientScopeMapping(attributeName, value, clientScopeId)));

    }

    @Override
    public ClientScopeToAttributeMapping findClientScopeAttribute(String clientScopeId, String attributeName) {
        return clientScopeDao.findClientScopeAttribute(clientScopeId, attributeName);
    }

    @Override
    public List<ClientScopeToAttributeMapping> findAllClientScopeAttributes(String clientScopeId) {
        return clientScopeDao.findAllClientScopeAttributes(clientScopeId).all();
    }
}
