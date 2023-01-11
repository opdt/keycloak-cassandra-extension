/*
 * Copyright 2022 IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.clientScope;

import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraJsonSerialization;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.ClientScopeRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopeValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopes;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.RoleUtils;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JBossLog
@RequiredArgsConstructor
@EqualsAndHashCode(of = "clientScopeEntity")
public class CassandraClientScopeAdapter implements ClientScopeModel {
    private static final String INTERNAL_ATTRIBUTE_PREFIX = "internal.";
    public static final String NAME = INTERNAL_ATTRIBUTE_PREFIX + "name";
    public static final String DESCRIPTION = INTERNAL_ATTRIBUTE_PREFIX + "description";
    public static final String PROTOCOL = INTERNAL_ATTRIBUTE_PREFIX + "protocol";
    public static final String PROTOCOL_MAPPERS = INTERNAL_ATTRIBUTE_PREFIX + "protocolMappers";
    public static final String SCOPE_MAPPINGS = INTERNAL_ATTRIBUTE_PREFIX + "scopeMappings";
    private final RealmModel realm;
    private final ClientScopeValue clientScopeEntity;
    private final ClientScopeRepository clientScopeRepository;

    @Override
    public String getId() {
        return clientScopeEntity.getId();
    }

    @Override
    public RealmModel getRealm() {
        return realm;
    }

    @Override
    public String getName() {
        return clientScopeEntity.getName();
    }

    @Override
    public void setName(String name) {
        updateAndRefresh(s -> s.setName(name));
    }

    @Override
    public String getDescription() {
        return getAttribute(DESCRIPTION);
    }

    @Override
    public void setDescription(String description) {
        setAttribute(DESCRIPTION, description);
    }

    @Override
    public String getProtocol() {
        return getAttribute(PROTOCOL);
    }

    @Override
    public void setProtocol(String protocol) {
        setAttribute(PROTOCOL, protocol);
    }

    @Override
    public Stream<ProtocolMapperModel> getProtocolMappersStream() {
        return getDeserializedAttributes(PROTOCOL_MAPPERS, ProtocolMapperModel.class).stream();
    }

    @Override
    public ProtocolMapperModel addProtocolMapper(ProtocolMapperModel model) {
        if (model.getId() == null) {
            String id = KeycloakModelUtils.generateId();
            model.setId(id);
        }
        if (model.getConfig() == null) {
            model.setConfig(new HashMap<>());
        }

        List<ProtocolMapperModel> protocolMappers = getDeserializedAttributes(PROTOCOL_MAPPERS, ProtocolMapperModel.class);
        protocolMappers.add(model);
        setSerializedAttributeValues(PROTOCOL_MAPPERS, protocolMappers);
        return model;
    }

    @Override
    public void removeProtocolMapper(ProtocolMapperModel mapping) {
        List<ProtocolMapperModel> protocolMappersWithoutMapping = getDeserializedAttributes(PROTOCOL_MAPPERS, ProtocolMapperModel.class).stream()
            .filter(e -> !e.getId().equals(mapping.getId()))
            .collect(Collectors.toList());
        setSerializedAttributeValues(PROTOCOL_MAPPERS, protocolMappersWithoutMapping);
    }

    @Override
    public void updateProtocolMapper(ProtocolMapperModel mapping) {
        List<ProtocolMapperModel> protocolMappersWithoutMapping = getDeserializedAttributes(PROTOCOL_MAPPERS, ProtocolMapperModel.class).stream()
            .filter(e -> !e.getId().equals(mapping.getId()))
            .collect(Collectors.toList());
        protocolMappersWithoutMapping.add(mapping);
        setSerializedAttributeValues(PROTOCOL_MAPPERS, protocolMappersWithoutMapping);
    }

    @Override
    public ProtocolMapperModel getProtocolMapperById(String id) {
        return getDeserializedAttributes(PROTOCOL_MAPPERS, ProtocolMapperModel.class).stream()
            .filter(e -> e.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    @Override
    public ProtocolMapperModel getProtocolMapperByName(String protocol, String name) {
        return getDeserializedAttributes(PROTOCOL_MAPPERS, ProtocolMapperModel.class).stream()
            .filter(e -> Objects.equals(e.getProtocol(), protocol))
            .filter(e -> Objects.equals(e.getName(), name))
            .findFirst()
            .orElse(null);
    }

    public Stream<RoleModel> getScopeMappingsStream() {
        List<String> scopeMappings = getAttributeValues(SCOPE_MAPPINGS);
        return scopeMappings == null ? Stream.empty() : scopeMappings.stream()
            .map(getRealm()::getRoleById)
            .filter(Objects::nonNull);
    }

    @Override
    public Stream<RoleModel> getRealmScopeMappingsStream() {
        return getScopeMappingsStream().filter(r -> RoleUtils.isRealmRole(r, getRealm()));
    }

    @Override
    public void addScopeMapping(RoleModel role) {
        if (role == null) {
            return;
        }

        Set<String> scopeMappings = new HashSet<>(getAttributeValues(SCOPE_MAPPINGS));
        scopeMappings.add(role.getId());
        setAttributeValues(SCOPE_MAPPINGS, new ArrayList<>(scopeMappings));
    }

    @Override
    public void deleteScopeMapping(RoleModel role) {
        if (role == null) {
            return;
        }

        List<String> scopeMappings = getAttributeValues(SCOPE_MAPPINGS);
        scopeMappings.remove(role.getId());
        setAttributeValues(SCOPE_MAPPINGS, scopeMappings);
    }

    @Override
    public boolean hasScope(RoleModel role) {
        return RoleUtils.hasRole(getScopeMappingsStream(), role);
    }

    @Override
    public void setAttribute(String name, String value) {
        if (name == null || value == null) {
            return;
        }

        updateAndRefresh(s -> s.getAttributes().put(name, Arrays.asList(value)));
    }

    @Override
    public void removeAttribute(String name) {
        if (name == null) {
            return;
        }
        updateAndRefresh(s -> s.getAttributes().remove(name));
    }

    @Override
    public String getAttribute(String name) {
        List<String> values = clientScopeEntity.getAttributes().getOrDefault(name, new ArrayList<>());
        return values.isEmpty() || values.iterator().next().isEmpty() ? null : values.iterator().next();
    }

    @Override
    public Map<String, String> getAttributes() {
        return clientScopeEntity.getAttributes().entrySet().stream()
            .filter(e -> !e.getKey().startsWith(INTERNAL_ATTRIBUTE_PREFIX))
            .filter(e -> e.getValue() != null && !e.getValue().isEmpty() && !e.getValue().iterator().next().isEmpty())
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().iterator().next()));
    }

    public void setAttributeValues(String name, List<String> values) {
        if (name == null || values == null) {
            return;
        }

        updateAndRefresh(s -> s.getAttributes().put(name, values));
    }

    private List<String> getAttributeValues(String name) {
        List<String> values = clientScopeEntity.getAttributes().getOrDefault(name, new ArrayList<>());
        return values.stream().filter(v -> v != null && !v.isEmpty()).collect(Collectors.toList());
    }

    private void setSerializedAttributeValues(String name, List<?> values) {
        List<String> attributeValues = values.stream()
            .map(value -> {
                try {
                    return CassandraJsonSerialization.writeValueAsString(value);
                } catch (IOException e) {
                    log.errorf("Cannot serialize %s (realm: %s, name: %s)", value, clientScopeEntity.getId(), name);
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());

        updateAndRefresh(s -> s.getAttributes().put(name, attributeValues));
    }

    private <T> List<T> getDeserializedAttributes(String name, Class<T> type) {
        List<String> values = clientScopeEntity.getAttributes().getOrDefault(name, new ArrayList<>());

        return values.stream()
            .map(value -> {
                try {
                    return CassandraJsonSerialization.readValue(value, type);
                } catch (IOException e) {
                    log.errorf("Cannot deserialize %s (realm: %s, name: %s, type: %s)", value, clientScopeEntity.getId(), name, type.getName());
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private void updateAndRefresh(Consumer<ClientScopeValue> clientScopeUpdateFunc) {
        // Read cached -> references stay intact
        ClientScopes clientScopes = clientScopeRepository.getClientScopesByRealmId(realm.getId());
        ClientScopeValue valueWithinClientScopes = clientScopes.getClientScopeById(clientScopeEntity.getId());
        clientScopeUpdateFunc.accept(valueWithinClientScopes);
        clientScopeUpdateFunc.accept(clientScopeEntity);
        clientScopeRepository.addOrUpdateClientScopes(clientScopes);
    }
}
