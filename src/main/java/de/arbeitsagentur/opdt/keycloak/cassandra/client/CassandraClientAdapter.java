package de.arbeitsagentur.opdt.keycloak.cassandra.client;

import com.fasterxml.jackson.core.type.TypeReference;
import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraJsonSerialization;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.ClientRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.Client;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.ClientToAttributeMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.RoleUtils;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JBossLog
@RequiredArgsConstructor
public class CassandraClientAdapter implements ClientModel {
    private static final String INTERNAL_ATTRIBUTE_PREFIX = "internal.";
    public static final String CLIENT_ID = INTERNAL_ATTRIBUTE_PREFIX + "clientId";
    public static final String NAME = INTERNAL_ATTRIBUTE_PREFIX + "name";
    public static final String DESCRIPTION = INTERNAL_ATTRIBUTE_PREFIX + "description";
    public static final String ENABLED = INTERNAL_ATTRIBUTE_PREFIX + "enabled";
    public static final String ALWAYS_DISPLAY_IN_CONSOLE = INTERNAL_ATTRIBUTE_PREFIX + "alwaysDisplayInConsole";
    public static final String SURROGATE_AUTH_REQUIRED = INTERNAL_ATTRIBUTE_PREFIX + "surrogateAuthRequired";
    public static final String WEB_ORIGINS = INTERNAL_ATTRIBUTE_PREFIX + "webOrigins";
    public static final String REDIRECT_URIS = INTERNAL_ATTRIBUTE_PREFIX + "redirectUris";
    public static final String MANAGEMENT_URL = INTERNAL_ATTRIBUTE_PREFIX + "managementUrl";
    public static final String ROOT_URL = INTERNAL_ATTRIBUTE_PREFIX + "rootUrl";
    public static final String BASE_URL = INTERNAL_ATTRIBUTE_PREFIX + "baseUrl";
    public static final String BEARER_ONLY = INTERNAL_ATTRIBUTE_PREFIX + "bearerOnly";
    public static final String NODE_RE_REGISTRATION_TIMEOUT = INTERNAL_ATTRIBUTE_PREFIX + "nodeReRegistrationTimeout";
    public static final String CLIENT_AUTHENTICATOR_TYPE = INTERNAL_ATTRIBUTE_PREFIX + "clientAuthenticatorType";
    public static final String SECRET = INTERNAL_ATTRIBUTE_PREFIX + "secret";
    public static final String REGISTRATION_TOKEN = INTERNAL_ATTRIBUTE_PREFIX + "registrationToken";
    public static final String PROTOCOL = INTERNAL_ATTRIBUTE_PREFIX + "protocol";
    public static final String FRONTCHANNEL_LOGOUT = INTERNAL_ATTRIBUTE_PREFIX + "frontchannelLogout";
    public static final String FULL_SCOPE_ALLOWED = INTERNAL_ATTRIBUTE_PREFIX + "fullScopeAllowed";
    public static final String PUBLIC_CLIENT = INTERNAL_ATTRIBUTE_PREFIX + "publicClient";
    public static final String CONSENT_REQUIRED = INTERNAL_ATTRIBUTE_PREFIX + "consentRequired";
    public static final String STANDARD_FLOW_ENABLED = INTERNAL_ATTRIBUTE_PREFIX + "standardFlowEnabled";
    public static final String IMPLICIT_FLOW_ENABLED = INTERNAL_ATTRIBUTE_PREFIX + "implicitFlowEnabled";
    public static final String DIRECT_ACCESS_GRANTS_ENABLED = INTERNAL_ATTRIBUTE_PREFIX + "directAccessGrantsEnabled";
    public static final String SERVICE_ACCOUNT_ENABLED = INTERNAL_ATTRIBUTE_PREFIX + "serviceAccountEnabled";
    public static final String NOT_BEFORE = INTERNAL_ATTRIBUTE_PREFIX + "notBefore";
    public static final String REGISTERED_NODES = INTERNAL_ATTRIBUTE_PREFIX + "registeredNodes";
    public static final String PROTOCOL_MAPPERS = INTERNAL_ATTRIBUTE_PREFIX + "protocolMappers";
    public static final String AUTHENTICATION_FLOW_BINDING_OVERRIDE = INTERNAL_ATTRIBUTE_PREFIX + "authenticationFlowBindingOverride";
    public static final String SCOPE_MAPPINGS = INTERNAL_ATTRIBUTE_PREFIX + "scopeMappings";
    public static final String CLIENT_SCOPES =  INTERNAL_ATTRIBUTE_PREFIX + "clientScopes";
    private final KeycloakSession session;
    private final Client clientEntity;
    private final ClientRepository clientRepository;

    @Override
    public void updateClient() {
        // TODO: what should we do here?
    }

    @Override
    public String getId() {
        return clientEntity.getId();
    }

    @Override
    public RoleModel getRole(String name) {
        return session.roles().getClientRole(this, name);
    }

    @Override
    public RoleModel addRole(String name) {
        return session.roles().addClientRole(this, name);
    }

    @Override
    public RoleModel addRole(String id, String name) {
        return session.roles().addClientRole(this, id, name);
    }

    @Override
    public boolean removeRole(RoleModel role) {
        return session.roles().removeRole(role);
    }

    @Override
    public Stream<RoleModel> getRolesStream() {
        return session.roles().getClientRolesStream(this, null, null);
    }

    @Override
    public Stream<RoleModel> getRolesStream(Integer firstResult, Integer maxResults) {
        return session.roles().getClientRolesStream(this, firstResult, maxResults);
    }

    @Override
    public Stream<RoleModel> searchForRolesStream(String search, Integer first, Integer max) {
        return session.roles().searchForClientRolesStream(this, search, first, max);
    }

    @Override
    public List<String> getDefaultRoles() {
        return ClientModel.super.getDefaultRoles();
    }

    @Override
    public Stream<String> getDefaultRolesStream() {
        return getRealm().getDefaultRole().getCompositesStream().filter(this::isClientRole).map(RoleModel::getName);
    }

    private boolean isClientRole(RoleModel role) {
        return role.isClientRole() && Objects.equals(role.getContainerId(), this.getId());
    }

    @Override
    @Deprecated
    public void addDefaultRole(String name) {
        getRealm().getDefaultRole().addCompositeRole(getOrAddRoleId(name));
    }

    private RoleModel getOrAddRoleId(String name) {
        RoleModel role = getRole(name);
        if (role == null) {
            role = addRole(name);
        }
        return role;
    }

    @Override
    public void removeDefaultRoles(String... defaultRoles) {
        for (String defaultRole : defaultRoles) {
            getRealm().getDefaultRole().removeCompositeRole(getRole(defaultRole));
        }

    }


    @Override
    public String getClientId() {
        return getAttribute(CLIENT_ID);
    }

    @Override
    public void setClientId(String clientId) {
        setAttribute(CLIENT_ID, clientId);
    }

    @Override
    public String getName() {
        return getAttribute(NAME);
    }

    @Override
    public void setName(String name) {
        setAttribute(NAME, name);
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
    public boolean isEnabled() {
        return getAttribute(ENABLED, false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        setAttribute(ENABLED, enabled);
    }

    @Override
    public boolean isAlwaysDisplayInConsole() {
        return getAttribute(ALWAYS_DISPLAY_IN_CONSOLE, false);
    }

    @Override
    public void setAlwaysDisplayInConsole(boolean alwaysDisplayInConsole) {
        setAttribute(ALWAYS_DISPLAY_IN_CONSOLE, alwaysDisplayInConsole);

    }

    @Override
    public boolean isSurrogateAuthRequired() {
        return getAttribute(SURROGATE_AUTH_REQUIRED, false);
    }

    @Override
    public void setSurrogateAuthRequired(boolean surrogateAuthRequired) {
        setAttribute(SURROGATE_AUTH_REQUIRED, surrogateAuthRequired);

    }

    @Override
    public Set<String> getWebOrigins() {
        return new HashSet<>(getAttributeValues(WEB_ORIGINS));
    }

    @Override
    public void setWebOrigins(Set<String> webOrigins) {
        setAttributeValues(WEB_ORIGINS, new ArrayList<>(webOrigins));
    }

    @Override
    public void addWebOrigin(String webOrigin) {
        Set<String> webOrigins = new HashSet<>(getAttributeValues(WEB_ORIGINS));
        webOrigins.add(webOrigin);
        setAttributeValues(WEB_ORIGINS, new ArrayList<>(webOrigins));
    }

    @Override
    public void removeWebOrigin(String webOrigin) {
        Set<String> webOrigins = new HashSet<>(getAttributeValues(WEB_ORIGINS));
        webOrigins.remove(webOrigin);
        setAttributeValues(WEB_ORIGINS, new ArrayList<>(webOrigins));
    }

    @Override
    public Set<String> getRedirectUris() {
        return new HashSet<>(getAttributeValues(REDIRECT_URIS));
    }

    @Override
    public void setRedirectUris(Set<String> redirectUris) {
        setAttributeValues(REDIRECT_URIS, new ArrayList<>(redirectUris));
    }

    @Override
    public void addRedirectUri(String redirectUri) {
        Set<String> rediretUris = new HashSet<>(getAttributeValues(REDIRECT_URIS));
        rediretUris.add(redirectUri);
        setAttributeValues(REDIRECT_URIS, new ArrayList<>(rediretUris));

    }

    @Override
    public void removeRedirectUri(String redirectUri) {
        Set<String> redirectUris = new HashSet<>(getAttributeValues(REDIRECT_URIS));
        redirectUris.remove(redirectUri);
        setAttributeValues(REDIRECT_URIS, new ArrayList<>(redirectUris));

    }

    @Override
    public String getManagementUrl() {
        return getAttribute(MANAGEMENT_URL);
    }

    @Override
    public void setManagementUrl(String url) {
        setAttribute(MANAGEMENT_URL, url);
    }

    @Override
    public String getRootUrl() {
        return getAttribute(ROOT_URL);
    }

    @Override
    public void setRootUrl(String url) {
        setAttribute(ROOT_URL, url);

    }

    @Override
    public String getBaseUrl() {
        return getAttribute(BASE_URL);
    }

    @Override
    public void setBaseUrl(String url) {
        setAttribute(BASE_URL, url);
    }

    @Override
    public boolean isBearerOnly() {
        return getAttribute(BEARER_ONLY, false);
    }

    @Override
    public void setBearerOnly(boolean only) {
        setAttribute(BEARER_ONLY, only);

    }

    @Override
    public int getNodeReRegistrationTimeout() {
        return getAttribute(NODE_RE_REGISTRATION_TIMEOUT, 0);
    }

    @Override
    public void setNodeReRegistrationTimeout(int timeout) {
        setAttribute(NODE_RE_REGISTRATION_TIMEOUT, timeout);
    }

    @Override
    public String getClientAuthenticatorType() {
        return getAttribute(CLIENT_AUTHENTICATOR_TYPE);
    }

    @Override
    public void setClientAuthenticatorType(String clientAuthenticatorType) {
        setAttribute(CLIENT_AUTHENTICATOR_TYPE, clientAuthenticatorType);

    }

    @Override
    public boolean validateSecret(String secret) {
        return MessageDigest.isEqual(secret.getBytes(), getAttribute(SECRET).getBytes());
    }

    @Override
    public String getSecret() {
        return getAttribute(SECRET);
    }

    @Override
    public void setSecret(String secret) {
        setAttribute(SECRET, secret);
    }

    @Override
    public String getRegistrationToken() {
        return getAttribute(REGISTRATION_TOKEN);
    }

    @Override
    public void setRegistrationToken(String registrationToken) {
        setAttribute(REGISTRATION_TOKEN, registrationToken);
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
    public void setAttribute(String name, String value) {
        if(value == null) {
            return;
        }

        clientRepository.insertOrUpdate(new ClientToAttributeMapping(clientEntity.getId(), name, List.of(value)));
    }

    @Override
    public void removeAttribute(String name) {
        clientRepository.deleteClientAttribute(clientEntity.getId(), name);
    }

    @Override
    public String getAttribute(String name) {
        ClientToAttributeMapping attribute = clientRepository.findClientAttribute(clientEntity.getId(), name);
        return attribute == null || attribute.getAttributeValues().isEmpty() || attribute.getAttributeValues().get(0).isEmpty() ? null : attribute.getAttributeValues().get(0);
    }

    @Override
    public Map<String, String> getAttributes() {
        return clientRepository.findAllClientAttributes(clientEntity.getId()).stream()
                .filter(e -> !e.getAttributeName().startsWith(INTERNAL_ATTRIBUTE_PREFIX))
                .filter(e -> e.getAttributeValues() != null && !e.getAttributeValues().isEmpty() && !e.getAttributeValues().get(0).isEmpty())
                .collect(Collectors.toMap(ClientToAttributeMapping::getAttributeName, e -> e.getAttributeValues().get(0)));
    }

    @Override
    public String getAuthenticationFlowBindingOverride(String binding) {
        Map<String, String> authenticationFlowBindingOverride = getDeserializedAttribute(AUTHENTICATION_FLOW_BINDING_OVERRIDE, new TypeReference<>() {
        });

        if(authenticationFlowBindingOverride == null || !authenticationFlowBindingOverride.containsKey(binding)) {
            return null;
        }
        return authenticationFlowBindingOverride.get(binding);
    }

    @Override
    public Map<String, String> getAuthenticationFlowBindingOverrides() {
        Map<String, String> authenticationFlowBindingOverride = getDeserializedAttribute(AUTHENTICATION_FLOW_BINDING_OVERRIDE, new TypeReference<>() {
        });

        return authenticationFlowBindingOverride == null ? Collections.emptyMap() : authenticationFlowBindingOverride;
    }

    @Override
    public void removeAuthenticationFlowBindingOverride(String binding) {
        Map<String, String> authenticationFlowBindingOverride = getDeserializedAttribute(AUTHENTICATION_FLOW_BINDING_OVERRIDE, new TypeReference<>() {
        });
        if(authenticationFlowBindingOverride == null) {
            return;
        }

        authenticationFlowBindingOverride.remove(binding);
        setSerializedAttributeValue(AUTHENTICATION_FLOW_BINDING_OVERRIDE, authenticationFlowBindingOverride);
    }

    @Override
    public void setAuthenticationFlowBindingOverride(String binding, String flowId) {
        Map<String, String> authenticationFlowBindingOverride = getDeserializedAttribute(AUTHENTICATION_FLOW_BINDING_OVERRIDE, new TypeReference<>() {
        });
        if(authenticationFlowBindingOverride == null) {
            authenticationFlowBindingOverride = new HashMap<>();
        }

        authenticationFlowBindingOverride.put(binding, flowId);
        setSerializedAttributeValue(AUTHENTICATION_FLOW_BINDING_OVERRIDE, authenticationFlowBindingOverride);
    }

    @Override
    public boolean isFrontchannelLogout() {
        return getAttribute(FRONTCHANNEL_LOGOUT, false);
    }

    @Override
    public void setFrontchannelLogout(boolean flag) {
        setAttribute(FRONTCHANNEL_LOGOUT, flag);

    }

    @Override
    public boolean isFullScopeAllowed() {
        return getAttribute(FULL_SCOPE_ALLOWED, false);
    }

    @Override
    public void setFullScopeAllowed(boolean value) {
        setAttribute(FULL_SCOPE_ALLOWED, value);
    }

    @Override
    public boolean isPublicClient() {
        return getAttribute(PUBLIC_CLIENT, false);
    }

    @Override
    public void setPublicClient(boolean flag) {
        setAttribute(PUBLIC_CLIENT, flag);
    }

    @Override
    public boolean isConsentRequired() {
        return getAttribute(CONSENT_REQUIRED, false);
    }

    @Override
    public void setConsentRequired(boolean consentRequired) {
        setAttribute(CONSENT_REQUIRED, consentRequired);
    }

    @Override
    public boolean isStandardFlowEnabled() {
        return getAttribute(STANDARD_FLOW_ENABLED, false);
    }

    @Override
    public void setStandardFlowEnabled(boolean standardFlowEnabled) {
        setAttribute(STANDARD_FLOW_ENABLED, standardFlowEnabled);
    }

    @Override
    public boolean isImplicitFlowEnabled() {
        return getAttribute(IMPLICIT_FLOW_ENABLED, false);
    }

    @Override
    public void setImplicitFlowEnabled(boolean implicitFlowEnabled) {
        setAttribute(IMPLICIT_FLOW_ENABLED, implicitFlowEnabled);
    }

    @Override
    public boolean isDirectAccessGrantsEnabled() {
        return getAttribute(DIRECT_ACCESS_GRANTS_ENABLED, false);
    }

    @Override
    public void setDirectAccessGrantsEnabled(boolean directAccessGrantsEnabled) {
        setAttribute(DIRECT_ACCESS_GRANTS_ENABLED, directAccessGrantsEnabled);
    }

    @Override
    public boolean isServiceAccountsEnabled() {
        return getAttribute(SERVICE_ACCOUNT_ENABLED, false);
    }

    @Override
    public void setServiceAccountsEnabled(boolean serviceAccountsEnabled) {
        setAttribute(SERVICE_ACCOUNT_ENABLED, serviceAccountsEnabled);
    }

    @Override
    public RealmModel getRealm() {
        return session.realms().getRealm(clientEntity.getRealmId());
    }

    @Override
    public void addClientScopes(Set<ClientScopeModel> clientScopes, boolean defaultScope) {
        Map<String, Boolean> clientScopeIds = getDeserializedAttribute(CLIENT_SCOPES, new TypeReference<>() {
        });

        if(clientScopeIds == null) {
            clientScopeIds = new HashMap<>();
        }

        clientScopeIds.putAll(clientScopes.stream()
                        .collect(Collectors.toMap(ClientScopeModel::getId, e -> defaultScope)));

        setSerializedAttributeValue(CLIENT_SCOPES, clientScopeIds);
    }

    @Override
    public void addClientScope(ClientScopeModel clientScope, boolean defaultScope) {
        addClientScopes(Collections.singleton(clientScope), defaultScope);
    }

    @Override
    public void removeClientScope(ClientScopeModel clientScope) {
        Map<String, Boolean> clientScopeIds = getDeserializedAttribute(CLIENT_SCOPES, new TypeReference<>() {
        });

        if(clientScopeIds == null) {
            return;
        }

        clientScopeIds.remove(clientScope.getId());
        setSerializedAttributeValue(CLIENT_SCOPES, clientScopeIds);
    }

    @Override
    public Map<String, ClientScopeModel> getClientScopes(boolean defaultScope) {
        Map<String, Boolean> clientScopeIds = getDeserializedAttribute(CLIENT_SCOPES, new TypeReference<>() {
        });

        if(clientScopeIds == null) {
            return Collections.emptyMap();
        }

        String clientProtocol = getProtocol() == null ? "openid-connect" : getProtocol();

        return clientScopeIds.entrySet().stream()
                .filter(e -> e.getValue() == defaultScope)
                .map(e -> session.clientScopes().getClientScopeById(getRealm(), e.getKey()))
                .filter(Objects::nonNull)
                .filter(clientScope -> Objects.equals(clientScope.getProtocol(), clientProtocol))
                .collect(Collectors.toMap(ClientScopeModel::getName, Function.identity()));
    }

    @Override
    public int getNotBefore() {
        return getAttribute(NOT_BEFORE, 0);
    }

    @Override
    public void setNotBefore(int notBefore) {
        setAttribute(NOT_BEFORE, notBefore);
    }

    @Override
    public Map<String, Integer> getRegisteredNodes() {
        Map<String, Integer> registeredNodes = getDeserializedAttribute(REGISTERED_NODES, new TypeReference<>() {
        });

        return registeredNodes == null ? Collections.emptyMap() : registeredNodes;
    }

    @Override
    public void registerNode(String nodeHost, int registrationTime) {
        Map<String, Integer> registeredNodes = getDeserializedAttribute(REGISTERED_NODES, new TypeReference<>() {
        });

        if(registeredNodes == null) {
            registeredNodes = new HashMap<>();
        }

        registeredNodes.put(nodeHost, registrationTime);
        setSerializedAttributeValue(REGISTERED_NODES, registeredNodes);
    }

    @Override
    public void unregisterNode(String nodeHost) {
        Map<String, Integer> registeredNodes = getDeserializedAttribute(REGISTERED_NODES, new TypeReference<>() {
        });

        if(registeredNodes == null) {
            registeredNodes = new HashMap<>();
        }

        registeredNodes.remove(nodeHost);
        setSerializedAttributeValue(REGISTERED_NODES, registeredNodes);
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

    @Override
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
        if(role == null) {
            return;
        }

        Set<String> scopeMappings = new HashSet<>(getAttributeValues(SCOPE_MAPPINGS));
        scopeMappings.add(role.getId());
        setAttributeValues(SCOPE_MAPPINGS, new ArrayList<>(scopeMappings));
    }

    @Override
    public void deleteScopeMapping(RoleModel role) {
        if(role == null) {
            return;
        }

        List<String> scopeMappings = getAttributeValues(SCOPE_MAPPINGS);
        scopeMappings.remove(role.getId());
        setAttributeValues(SCOPE_MAPPINGS, scopeMappings);
    }

    @Override
    public boolean hasDirectScope(RoleModel role) {
        final String id = role == null ? null : role.getId();
        final Collection<String> scopeMappings =  getAttributeValues(SCOPE_MAPPINGS);
        if (id != null && scopeMappings != null && scopeMappings.contains(id)) {
            return true;
        }

        return getRolesStream().anyMatch(r -> (Objects.equals(r, role)));
    }

    @Override
    public boolean hasScope(RoleModel role) {
        if (isFullScopeAllowed()) return true;

        final String id = role == null ? null : role.getId();
        final Collection<String> scopeMappings = getAttributeValues(SCOPE_MAPPINGS);
        if (id != null && scopeMappings != null && scopeMappings.contains(id)) {
            return true;
        }

        if (getScopeMappingsStream().anyMatch(r -> r.hasRole(role))) {
            return true;
        }

        return getRolesStream().anyMatch(r -> (Objects.equals(r, role) || r.hasRole(role)));
    }

    private void setAttribute(String name, boolean value) {
        setAttribute(name, Boolean.toString(value));
    }
    private boolean getAttribute(String name, boolean defaultValue) {
        String v = getAttribute(name);
        return v != null ? Boolean.valueOf(v) : defaultValue;
    }

    private void setAttribute(String name, int value) {
        setAttribute(name, Integer.toString(value));
    }
    private int getAttribute(String name, int defaultValue) {
        String v = getAttribute(name);
        return v != null ? Integer.valueOf(v) : defaultValue;
    }

    public void setAttributeValues(String name, List<String> values) {
        clientRepository.insertOrUpdate(new ClientToAttributeMapping(clientEntity.getId(), name, values));
    }

    private List<String> getAttributeValues(String name) {
        ClientToAttributeMapping attribute = clientRepository.findClientAttribute(clientEntity.getId(), name);
        return attribute == null ? new ArrayList<>() : attribute.getAttributeValues().stream().filter(v -> v != null && !v.isEmpty()).collect(Collectors.toList());
    }

    private <T> void setSerializedAttributeValue(String name, T value) {
        setSerializedAttributeValues(name, value instanceof List ? (List<Object>) value : Arrays.asList(value));
    }

    private void setSerializedAttributeValues(String name, List<?> values) {
        List<String> attributeValues = values.stream()
                .map(value -> {
                    try {
                        return CassandraJsonSerialization.writeValueAsString(value);
                    } catch (IOException e) {
                        log.errorf("Cannot serialize %s (client: %s, name: %s)", value, clientEntity.getId(), name);
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toCollection(ArrayList::new));

        clientRepository.insertOrUpdate(new ClientToAttributeMapping(clientEntity.getId(), name, attributeValues));
    }

    private <T> T getDeserializedAttribute(String name, TypeReference<T> type) {
        return getDeserializedAttributes(name, type).stream().findFirst().orElse(null);
    }

    private <T> T getDeserializedAttribute(String name, Class<T> type) {
        return getDeserializedAttributes(name, type).stream().findFirst().orElse(null);
    }

    private <T> List<T> getDeserializedAttributes(String name, TypeReference<T> type) {
        ClientToAttributeMapping attribute = clientRepository.findClientAttribute(clientEntity.getId(), name);
        if (attribute == null) {
            return new ArrayList<>();
        }

        return attribute.getAttributeValues().stream()
                .map(value -> {
                    try {
                        return CassandraJsonSerialization.readValue(value, type);
                    } catch (IOException e) {
                        log.errorf("Cannot deserialize %s (client: %s, name: %s)", value, clientEntity.getId(), name);
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private <T> List<T> getDeserializedAttributes(String name, Class<T> type) {
        ClientToAttributeMapping attribute = clientRepository.findClientAttribute(clientEntity.getId(), name);
        if (attribute == null) {
            return new ArrayList<>();
        }

        return attribute.getAttributeValues().stream()
                .map(value -> {
                    try {
                        return CassandraJsonSerialization.readValue(value, type);
                    } catch (IOException e) {
                        log.errorf("Cannot deserialize %s (client: %s, name: %s, type: %s)", value, clientEntity.getId(), name, type.getName());
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
