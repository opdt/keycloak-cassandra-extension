/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.parameters;

import com.google.common.collect.ImmutableSet;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.Config;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.KeycloakModelParameters;
import org.keycloak.authorization.store.StoreFactorySpi;
import org.keycloak.credential.CredentialSpi;
import org.keycloak.credential.OTPCredentialProviderFactory;
import org.keycloak.credential.PasswordCredentialProviderFactory;
import org.keycloak.credential.hash.PasswordHashSpi;
import org.keycloak.credential.hash.Pbkdf2Sha256PasswordHashProviderFactory;
import org.keycloak.device.DeviceRepresentationProviderFactory;
import org.keycloak.device.DeviceRepresentationProviderFactoryImpl;
import org.keycloak.device.DeviceRepresentationSpi;
import org.keycloak.events.EventStoreSpi;
import org.keycloak.keys.*;
import org.keycloak.models.*;
import org.keycloak.models.locking.GlobalLockProviderSpi;
import org.keycloak.models.locking.NoneGlobalLockProviderFactory;
import org.keycloak.models.map.authSession.MapRootAuthenticationSessionProviderFactory;
import org.keycloak.models.map.authorization.MapAuthorizationStoreFactory;
import org.keycloak.models.map.client.MapClientProviderFactory;
import org.keycloak.models.map.clientscope.MapClientScopeProviderFactory;
import org.keycloak.models.map.deploymentState.MapDeploymentStateProviderFactory;
import org.keycloak.models.map.events.MapEventStoreProviderFactory;
import org.keycloak.models.map.group.MapGroupProviderFactory;
import org.keycloak.models.map.keys.MapPublicKeyStorageProviderFactory;
import org.keycloak.models.map.loginFailure.MapUserLoginFailureProviderFactory;
import org.keycloak.models.map.realm.MapRealmProviderFactory;
import org.keycloak.models.map.role.MapRoleProviderFactory;
import org.keycloak.models.map.singleUseObject.MapSingleUseObjectProviderFactory;
import org.keycloak.models.map.storage.MapStorageSpi;
import org.keycloak.models.map.storage.chm.ConcurrentHashMapStorageProviderFactory;
import org.keycloak.models.map.user.MapUserProviderFactory;
import org.keycloak.models.map.userSession.MapUserSessionProviderFactory;
import org.keycloak.policy.*;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;
import org.keycloak.services.clientpolicy.ClientPolicyManagerSpi;
import org.keycloak.services.clientpolicy.DefaultClientPolicyManagerFactory;
import org.keycloak.services.clientregistration.policy.ClientRegistrationPolicySpi;
import org.keycloak.services.clientregistration.policy.impl.*;
import org.keycloak.sessions.AuthenticationSessionSpi;

import java.util.Set;

/**
 * @author hmlnarik
 */
public class Map extends KeycloakModelParameters {

    static final Set<Class<? extends Spi>> ALLOWED_SPIS = ImmutableSet.<Class<? extends Spi>>builder()
        .add(AuthenticationSessionSpi.class)
        .add(SingleUseObjectSpi.class)
        .add(PublicKeyStorageSpi.class)
        .add(MapStorageSpi.class)
        .add(ClientPolicyManagerSpi.class)
        .add(KeySpi.class)
        .add(ClientRegistrationPolicySpi.class)
        .add(CredentialSpi.class)
        .add(PasswordPolicyManagerSpi.class)
        .add(PasswordHashSpi.class)
        .add(PasswordPolicySpi.class)
        .add(DeviceRepresentationSpi.class)
        .build();

    static final Set<Class<? extends ProviderFactory>> ALLOWED_FACTORIES = ImmutableSet.<Class<? extends ProviderFactory>>builder()
        .add(MapAuthorizationStoreFactory.class)
        .add(MapClientProviderFactory.class)
        .add(MapClientScopeProviderFactory.class)
        .add(MapGroupProviderFactory.class)
        .add(MapRealmProviderFactory.class)
        .add(MapRoleProviderFactory.class)
        .add(MapRootAuthenticationSessionProviderFactory.class)
        .add(MapDeploymentStateProviderFactory.class)
        .add(MapUserProviderFactory.class)
        .add(MapUserSessionProviderFactory.class)
        .add(MapUserLoginFailureProviderFactory.class)
        .add(NoneGlobalLockProviderFactory.class)
        .add(MapEventStoreProviderFactory.class)
        .add(SingleUseObjectProviderFactory.class)
        .add(MapPublicKeyStorageProviderFactory.class)
        .add(DefaultClientPolicyManagerFactory.class)
        .add(GeneratedAesKeyProviderFactory.class)
        .add(GeneratedHmacKeyProviderFactory.class)
        .add(GeneratedEcdsaKeyProviderFactory.class)
        .add(ImportedRsaEncKeyProviderFactory.class)
        .add(ImportedRsaKeyProviderFactory.class)
        .add(GeneratedRsaEncKeyProviderFactory.class)
        .add(GeneratedRsaKeyProviderFactory.class)
        .add(ProtocolMappersClientRegistrationPolicyFactory.class)
        .add(ClientDisabledClientRegistrationPolicyFactory.class)
        .add(TrustedHostClientRegistrationPolicyFactory.class)
        .add(ConsentRequiredClientRegistrationPolicyFactory.class)
        .add(ClientScopesClientRegistrationPolicyFactory.class)
        .add(ScopeClientRegistrationPolicyFactory.class)
        .add(MaxClientsClientRegistrationPolicyFactory.class)
        .add(OTPCredentialProviderFactory.class)
        .add(PasswordCredentialProviderFactory.class)
        .add(DefaultPasswordPolicyManagerProviderFactory.class)
        .add(Pbkdf2Sha256PasswordHashProviderFactory.class)
        .add(HashAlgorithmPasswordPolicyProviderFactory.class)
        .add(HashIterationsPasswordPolicyProviderFactory.class)
        .add(HistoryPasswordPolicyProviderFactory.class)
        .add(ForceExpiredPasswordPolicyProviderFactory.class)
        .add(DeviceRepresentationProviderFactoryImpl.class)
        .build();

    public Map() {
        super(ALLOWED_SPIS, ALLOWED_FACTORIES);
    }

    @Override
    public void updateConfig(Config cf) {
        cf.spi(AuthenticationSessionSpi.PROVIDER_ID).defaultProvider(MapRootAuthenticationSessionProviderFactory.PROVIDER_ID)
            .spi(SingleUseObjectSpi.NAME).defaultProvider(MapSingleUseObjectProviderFactory.PROVIDER_ID)
            .spi("client").defaultProvider(MapClientProviderFactory.PROVIDER_ID)
            .spi("clientScope").defaultProvider(MapClientScopeProviderFactory.PROVIDER_ID)
            .spi("group").defaultProvider(MapGroupProviderFactory.PROVIDER_ID)
            .spi("realm").defaultProvider(MapRealmProviderFactory.PROVIDER_ID)
            .spi("role").defaultProvider(MapRoleProviderFactory.PROVIDER_ID)
            .spi(DeploymentStateSpi.NAME).defaultProvider(MapDeploymentStateProviderFactory.PROVIDER_ID)
            .spi(StoreFactorySpi.NAME).defaultProvider(MapAuthorizationStoreFactory.PROVIDER_ID)
            .spi("user").defaultProvider(MapUserProviderFactory.PROVIDER_ID)
            .spi(UserSessionSpi.NAME).defaultProvider(MapUserSessionProviderFactory.PROVIDER_ID)
            .spi(UserLoginFailureSpi.NAME).defaultProvider(MapUserLoginFailureProviderFactory.PROVIDER_ID)
            .spi(GlobalLockProviderSpi.GLOBAL_LOCK).defaultProvider(NoneGlobalLockProviderFactory.PROVIDER_ID)
            .spi(EventStoreSpi.NAME).defaultProvider(MapEventStoreProviderFactory.PROVIDER_ID)
            .spi("publicKeyStorage").defaultProvider(MapPublicKeyStorageProviderFactory.PROVIDER_ID)
            .spi("client-policy-manager").defaultProvider("default")
            .spi("password-hashing")
                .provider(Pbkdf2Sha256PasswordHashProviderFactory.ID)
            .spi("password-policy-manager").defaultProvider("default")
            .spi("password-policy")
                .provider(PasswordPolicy.PASSWORD_HISTORY_ID)
                .provider(PasswordPolicy.FORCE_EXPIRED_ID)
                .provider(PasswordPolicy.HASH_ALGORITHM_ID)
                .provider(PasswordPolicy.HASH_ITERATIONS_ID)
            .spi("credential")
                .provider(PasswordCredentialProviderFactory.PROVIDER_ID)
                .provider(OTPCredentialProviderFactory.PROVIDER_ID)
            .spi("keys")
                .provider(GeneratedAesKeyProviderFactory.ID)
                .provider(GeneratedHmacKeyProviderFactory.ID)
                .provider(GeneratedEcdsaKeyProviderFactory.ID)
                .provider(ImportedRsaEncKeyProviderFactory.ID)
                .provider(ImportedRsaKeyProviderFactory.ID)
                .provider(GeneratedRsaEncKeyProviderFactory.ID)
                .provider(GeneratedRsaKeyProviderFactory.ID)
            .spi("client-registration-policy")
                .provider(ProtocolMappersClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(ClientDisabledClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(TrustedHostClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(ConsentRequiredClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(ClientScopesClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(ScopeClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(MaxClientsClientRegistrationPolicyFactory.PROVIDER_ID)
            .spi(DeviceRepresentationSpi.NAME)
            .defaultProvider(DeviceRepresentationProviderFactoryImpl.PROVIDER_ID)
        ;
        cf.spi(MapStorageSpi.NAME).provider(ConcurrentHashMapStorageProviderFactory.PROVIDER_ID).config("keyType.single-use-objects", "string");
    }
}
