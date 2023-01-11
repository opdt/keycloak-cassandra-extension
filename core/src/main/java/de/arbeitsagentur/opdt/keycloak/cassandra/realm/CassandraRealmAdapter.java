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
package de.arbeitsagentur.opdt.keycloak.cassandra.realm;

import com.fasterxml.jackson.core.type.TypeReference;
import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraJsonSerialization;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.ClientInitialAccess;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.Realm;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.common.enums.SslRequired;
import org.keycloak.common.util.Time;
import org.keycloak.component.ComponentFactory;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.*;
import org.keycloak.models.map.common.TimeAdapter;
import org.keycloak.models.utils.ComponentUtil;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;

@EqualsAndHashCode(of = "realmEntity")
@JBossLog
@RequiredArgsConstructor
public class CassandraRealmAdapter implements RealmModel {
    private static final String INTERNAL_ATTRIBUTE_PREFIX = "internal.";
    private static final String COMPONENT_PROVIDER_EXISTS_DISABLED = "component.provider.exists.disabled"; // Copied from MapRealmAdapter
    public static final String COMPONENT_PROVIDER_TYPE = INTERNAL_ATTRIBUTE_PREFIX + "componentProviderType";
    public static final String DEFAULT_GROUP_IDS = INTERNAL_ATTRIBUTE_PREFIX + "defaultGroupIds";
    public static final String DEFAULT_ROLE_ID = INTERNAL_ATTRIBUTE_PREFIX + "defaultRoleId";
    public static final String DEFAULT_CLIENT_SCOPE_ID = INTERNAL_ATTRIBUTE_PREFIX + "defaultClientScopeId";
    public static final String OPTIONAL_CLIENT_SCOPE_ID = INTERNAL_ATTRIBUTE_PREFIX + "optionalClientScopeId";
    public static final String DISPLAY_NAME = INTERNAL_ATTRIBUTE_PREFIX + "displayName";
    public static final String DISPLAY_NAME_HTML = INTERNAL_ATTRIBUTE_PREFIX + "displayNameHtml";
    public static final String ENABLED = INTERNAL_ATTRIBUTE_PREFIX + "enabled";
    public static final String SSL_REQUIRED = INTERNAL_ATTRIBUTE_PREFIX + "sslRequired";
    public static final String IS_REGISTRATION_ALLOWED = INTERNAL_ATTRIBUTE_PREFIX + "isRegistrationAllowed";
    public static final String IS_REGISTRATION_EMAIL_AS_USERNAME = INTERNAL_ATTRIBUTE_PREFIX + "isRegistrationEmailAsUsername";
    public static final String IS_REMEMBER_ME = INTERNAL_ATTRIBUTE_PREFIX + "isRememberMe";
    public static final String IS_EDIT_USERNAME_ALLOWED = INTERNAL_ATTRIBUTE_PREFIX + "isEditUsernameAllowed";
    public static final String IS_USER_MANAGED_ACCESS_ALLOWED = INTERNAL_ATTRIBUTE_PREFIX + "isUserManagedAccessAllowed";
    public static final String IS_BRUTE_FORCE_PROTECTED = INTERNAL_ATTRIBUTE_PREFIX + "isBruteForceProtected";
    public static final String IS_PERMANENT_LOCKOUT = INTERNAL_ATTRIBUTE_PREFIX + "isPermanentLockout";
    public static final String MAX_FAILURE_WAIT_SECONDS = INTERNAL_ATTRIBUTE_PREFIX + "maxFailureWaitSeconds";
    public static final String WAIT_INCREMENT_SECONDS = INTERNAL_ATTRIBUTE_PREFIX + "waitIncrementSeconds";
    public static final String MINIMUM_QUICK_LOGIN_WAIT_SECONDS = INTERNAL_ATTRIBUTE_PREFIX + "minimumQuickLoginWaitSeconds";
    public static final String QUICK_LOGIN_CHECK_MILLI_SECONDS = INTERNAL_ATTRIBUTE_PREFIX + "quickLoginCheckMilliSeconds";
    public static final String MAX_DELTA_TIME_SECONDS = INTERNAL_ATTRIBUTE_PREFIX + "maxDeltaTimeSeconds";
    public static final String FAILURE_FACTOR = INTERNAL_ATTRIBUTE_PREFIX + "failureFactor";
    public static final String VERIFY_EMAIL = INTERNAL_ATTRIBUTE_PREFIX + "verifyEmail";
    public static final String LOGIN_WITH_EMAIL_ALLOWED = INTERNAL_ATTRIBUTE_PREFIX + "loginWithEmailAllowed";
    public static final String IS_DUPLICATE_EMAILS_ALLOWED = INTERNAL_ATTRIBUTE_PREFIX + "isDuplicateEmailsAllowed";
    public static final String IS_RESET_PASSWORD_ALLOWED = INTERNAL_ATTRIBUTE_PREFIX + "isResetPasswordAllowed";
    public static final String DEFAULT_SIG_ALGORITHM = INTERNAL_ATTRIBUTE_PREFIX + "defaultSigAlgorithm";
    public static final String IS_REVOKE_REFRESH_TOKEN = INTERNAL_ATTRIBUTE_PREFIX + "isRevokeRefreshToken";
    public static final String REFRESH_TOKEN_MAX_REUSE = INTERNAL_ATTRIBUTE_PREFIX + "refreshTokenMaxReuse";
    public static final String SSO_SESSION_IDLE_TIMEOUT = INTERNAL_ATTRIBUTE_PREFIX + "ssoSessionIdleTimeout";
    public static final String SSO_SESSION_MAX_LIFESPAN = INTERNAL_ATTRIBUTE_PREFIX + "ssoSessionMaxLifespan";
    public static final String SSO_SESSION_IDLE_TIMEOUT_REMEMBER_ME = INTERNAL_ATTRIBUTE_PREFIX + "ssoSessionIdleTimeoutRememberMe";
    public static final String SSO_SESSION_MAX_LIFESPAN_REMEMBER_ME = INTERNAL_ATTRIBUTE_PREFIX + "ssoSessionMaxLifespanRememberMe";
    public static final String OFFLINE_SESSION_IDLE_TIMEOUT = INTERNAL_ATTRIBUTE_PREFIX + "offlineSessionIdleTimeout";
    public static final String ACCESS_TOKEN_LIFESPAN = INTERNAL_ATTRIBUTE_PREFIX + "accessTokenLifespan";
    public static final String IS_OFFLINE_SESSION_MAX_LIFESPAN_ENABLED = INTERNAL_ATTRIBUTE_PREFIX + "isOfflineSessionMaxLifespanEnabled";
    public static final String OFFLINE_SESSION_MAX_LIFESPAN = INTERNAL_ATTRIBUTE_PREFIX + "offlineSessionMaxLifespan";
    public static final String CLIENT_SESSION_IDLE_TIMEOUT = INTERNAL_ATTRIBUTE_PREFIX + "clientSessionIdleTimeout";
    public static final String CLIENT_SESSION_MAX_LIFESPAN = INTERNAL_ATTRIBUTE_PREFIX + "clientSessionMaxLifespan";
    public static final String CLIENT_OFFLINE_SESSION_IDLE_TIMEOUT = INTERNAL_ATTRIBUTE_PREFIX + "clientOfflineSessionIdleTimeout";
    public static final String CLIENT_OFFLINE_SESSION_MAX_LIFESPAN = INTERNAL_ATTRIBUTE_PREFIX + "clientOfflineSessionMaxLifespan";
    public static final String ACCESS_TOKEN_LIFESPAN_FOR_IMPLICIT_FLOW = INTERNAL_ATTRIBUTE_PREFIX + "accessTokenLifespanForImplicitFlow";
    public static final String ACCESS_CODE_LIFESPAN = INTERNAL_ATTRIBUTE_PREFIX + "accessCodeLifespan";
    public static final String ACCESS_CODE_LIFESPAN_USER_ACTION = INTERNAL_ATTRIBUTE_PREFIX + "accessCodeLifespanUserAction";
    public static final String ACCESS_CODE_LIFESPAN_LOGIN = INTERNAL_ATTRIBUTE_PREFIX + "accessCodeLifespanLogin";
    public static final String ACTION_TOKEN_GENERATED_BY_ADMIN_LIFESPAN = INTERNAL_ATTRIBUTE_PREFIX + "actionTokenGeneratedByAdminLifespan";
    public static final String ACTION_TOKEN_GENERATED_BY_USER_LIFESPAN = INTERNAL_ATTRIBUTE_PREFIX + "actionTokenGeneratedByUserLifespan";
    public static final String PASSWORD_POLICY = INTERNAL_ATTRIBUTE_PREFIX + "passwordPolicy";
    public static final String REQUIRED_CREDENTIALS = INTERNAL_ATTRIBUTE_PREFIX + "requiredCredentials";
    public static final String OTP_POLICY = INTERNAL_ATTRIBUTE_PREFIX + "otpPolicy";
    public static final String WEB_AUTHN_POLICY = INTERNAL_ATTRIBUTE_PREFIX + "webAuthnPolicy";
    public static final String WEB_AUTHN_POLICY_PASSWORDLESS = INTERNAL_ATTRIBUTE_PREFIX + "webAuthnPolicyPasswordless";
    public static final String BROWSER_SECURITY_HEADERS = INTERNAL_ATTRIBUTE_PREFIX + "browserSecurityHeaders";
    public static final String SMTP_CONFIG = INTERNAL_ATTRIBUTE_PREFIX + "smtpConfig";
    public static final String BROWSER_FLOW = INTERNAL_ATTRIBUTE_PREFIX + "browserFlow";
    public static final String REGISTRATION_FLOW = INTERNAL_ATTRIBUTE_PREFIX + "registrationFlow";
    public static final String DIRECT_GRANT_FLOW = INTERNAL_ATTRIBUTE_PREFIX + "directGrantFlow";
    public static final String RESET_CREDENTIALS_FLOW = INTERNAL_ATTRIBUTE_PREFIX + "resetCredentialsFlow";
    public static final String CLIENT_AUTHENTICATION_FLOW = INTERNAL_ATTRIBUTE_PREFIX + "clientAuthenticationFlow";
    public static final String DOCKER_AUTHENTICATION_FLOW = INTERNAL_ATTRIBUTE_PREFIX + "dockerAuthenticationFlow";
    public static final String AUTHENTICATION_FLOWS = INTERNAL_ATTRIBUTE_PREFIX + "authenticationFlows";
    public static final String AUTHENTICATION_EXECUTION_MODELS = INTERNAL_ATTRIBUTE_PREFIX + "authenticationExecutionModels";
    public static final String AUTHENTICATOR_CONFIG_MODELS = INTERNAL_ATTRIBUTE_PREFIX + "authenticatorConfigModels";
    public static final String REQUIRED_ACTION_PROVIDER_MODELS = INTERNAL_ATTRIBUTE_PREFIX + "requiredActionProviderModels";
    public static final String IDENTITY_PROVIDERS = INTERNAL_ATTRIBUTE_PREFIX + "identityProviders";
    public static final String IDENTITY_PROVIDER_MAPPERS = INTERNAL_ATTRIBUTE_PREFIX + "identityProviderMappers";
    public static final String COMPONENTS = INTERNAL_ATTRIBUTE_PREFIX + "components";
    public static final String LOGIN_THEME = INTERNAL_ATTRIBUTE_PREFIX + "loginTheme";
    public static final String ACCOUNT_THEME = INTERNAL_ATTRIBUTE_PREFIX + "accountTheme";
    public static final String ADMIN_THEME = INTERNAL_ATTRIBUTE_PREFIX + "adminTheme";
    public static final String EMAIL_THEME = INTERNAL_ATTRIBUTE_PREFIX + "emailTheme";
    public static final String NOT_BEFORE = INTERNAL_ATTRIBUTE_PREFIX + "notBefore";
    public static final String IS_EVENTS_ENABLED = INTERNAL_ATTRIBUTE_PREFIX + "isEventsEnabled";
    public static final String EVENTS_EXPIRATION = INTERNAL_ATTRIBUTE_PREFIX + "eventsExpiration";
    public static final String EVENT_LISTENERS = INTERNAL_ATTRIBUTE_PREFIX + "eventListeners";
    public static final String ENABLED_EVENT_TYPES = INTERNAL_ATTRIBUTE_PREFIX + "enabledEventTypes";
    public static final String IS_ADMIN_EVENTS_ENABLED = INTERNAL_ATTRIBUTE_PREFIX + "isAdminEventsEnabled";
    public static final String IS_ADMIN_EVENTS_DETAILS_ENABLED = INTERNAL_ATTRIBUTE_PREFIX + "isAdminEventsDetailsEnabled";
    public static final String MASTER_ADMIN_CLIENT_ID = INTERNAL_ATTRIBUTE_PREFIX + "masterAdminClientId";
    public static final String IS_INTERNATIONALIZATION_ENABLED = INTERNAL_ATTRIBUTE_PREFIX + "isInternationalizationEnabled";
    public static final String SUPPORTED_LOCALES = INTERNAL_ATTRIBUTE_PREFIX + "supportedLocales";
    public static final String DEFAULT_LOCALE = INTERNAL_ATTRIBUTE_PREFIX + "defaultLocale";
    public static final String LOCALIZATION_TEXTS = INTERNAL_ATTRIBUTE_PREFIX + "localizationTexts";

    private final KeycloakSession session;
    private final Realm realmEntity;
    private final RealmRepository realmRepository;

    @Override
    public String getId() {
        return realmEntity.getId();
    }

    @Override
    public String getName() {
        return realmEntity.getName();
    }

    @Override
    public void setName(String name) {
        realmEntity.setName(name);
        realmRepository.insertOrUpdate(realmEntity);
    }

    @Override
    public String getDisplayName() {
        return getAttribute(DISPLAY_NAME);
    }

    @Override
    public void setDisplayName(String displayName) {
        setAttribute(DISPLAY_NAME, displayName);
    }

    @Override
    public String getDisplayNameHtml() {
        return getAttribute(DISPLAY_NAME_HTML);
    }

    @Override
    public void setDisplayNameHtml(String displayNameHtml) {
        setAttribute(DISPLAY_NAME_HTML, displayNameHtml);
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
    public SslRequired getSslRequired() {
        String sslReq = getAttribute(SSL_REQUIRED);
        return sslReq == null ? null : SslRequired.valueOf(sslReq);
    }

    @Override
    public void setSslRequired(SslRequired sslRequired) {
        setAttribute(SSL_REQUIRED, sslRequired == null ? null : sslRequired.name());
    }

    @Override
    public boolean isRegistrationAllowed() {
        return getAttribute(IS_REGISTRATION_ALLOWED, false);
    }

    @Override
    public void setRegistrationAllowed(boolean registrationAllowed) {
        setAttribute(IS_REGISTRATION_ALLOWED, registrationAllowed);
    }

    @Override
    public boolean isRegistrationEmailAsUsername() {
        return getAttribute(IS_REGISTRATION_EMAIL_AS_USERNAME, false);
    }

    @Override
    public void setRegistrationEmailAsUsername(boolean registrationEmailAsUsername) {
        setAttribute(IS_REGISTRATION_EMAIL_AS_USERNAME, registrationEmailAsUsername);
    }

    @Override
    public boolean isRememberMe() {
        return getAttribute(IS_REMEMBER_ME, false);
    }

    @Override
    public void setRememberMe(boolean rememberMe) {
        setAttribute(IS_REMEMBER_ME, rememberMe);
    }

    @Override
    public boolean isEditUsernameAllowed() {
        return getAttribute(IS_EDIT_USERNAME_ALLOWED, false);
    }

    @Override
    public void setEditUsernameAllowed(boolean editUsernameAllowed) {
        setAttribute(IS_EDIT_USERNAME_ALLOWED, editUsernameAllowed);
    }

    @Override
    public boolean isUserManagedAccessAllowed() {
        return getAttribute(IS_USER_MANAGED_ACCESS_ALLOWED, false);
    }

    @Override
    public void setUserManagedAccessAllowed(boolean userManagedAccessAllowed) {
        setAttribute(IS_USER_MANAGED_ACCESS_ALLOWED, userManagedAccessAllowed);
    }


    @Override
    public boolean isBruteForceProtected() {
        return getAttribute(IS_BRUTE_FORCE_PROTECTED, false);
    }

    @Override
    public void setBruteForceProtected(boolean value) {
        setAttribute(IS_BRUTE_FORCE_PROTECTED, value);
    }

    @Override
    public boolean isPermanentLockout() {
        return getAttribute(IS_PERMANENT_LOCKOUT, false);
    }

    @Override
    public void setPermanentLockout(boolean val) {
        setAttribute(IS_PERMANENT_LOCKOUT, val);
    }

    @Override
    public int getMaxFailureWaitSeconds() {
        return getAttribute(MAX_FAILURE_WAIT_SECONDS, 0);
    }

    @Override
    public void setMaxFailureWaitSeconds(int val) {
        setAttribute(MAX_FAILURE_WAIT_SECONDS, val);
    }

    @Override
    public int getWaitIncrementSeconds() {
        return getAttribute(WAIT_INCREMENT_SECONDS, 0);
    }

    @Override
    public void setWaitIncrementSeconds(int val) {
        setAttribute(WAIT_INCREMENT_SECONDS, val);
    }

    @Override
    public int getMinimumQuickLoginWaitSeconds() {
        return getAttribute(MINIMUM_QUICK_LOGIN_WAIT_SECONDS, 0);
    }

    @Override
    public void setMinimumQuickLoginWaitSeconds(int val) {
        setAttribute(MINIMUM_QUICK_LOGIN_WAIT_SECONDS, val);
    }

    @Override
    public long getQuickLoginCheckMilliSeconds() {
        return getAttribute(QUICK_LOGIN_CHECK_MILLI_SECONDS, 0L);
    }

    @Override
    public void setQuickLoginCheckMilliSeconds(long val) {
        setAttribute(QUICK_LOGIN_CHECK_MILLI_SECONDS, val);
    }

    @Override
    public int getMaxDeltaTimeSeconds() {
        return getAttribute(MAX_DELTA_TIME_SECONDS, 0);
    }

    @Override
    public void setMaxDeltaTimeSeconds(int val) {
        setAttribute(MAX_DELTA_TIME_SECONDS, val);
    }

    @Override
    public int getFailureFactor() {
        return getAttribute(FAILURE_FACTOR, 0);
    }

    @Override
    public void setFailureFactor(int failureFactor) {
        setAttribute(FAILURE_FACTOR, failureFactor);
    }

    @Override
    public boolean isVerifyEmail() {
        return getAttribute(VERIFY_EMAIL, false);
    }

    @Override
    public void setVerifyEmail(boolean verifyEmail) {
        setAttribute(VERIFY_EMAIL, verifyEmail);
    }

    @Override
    public boolean isLoginWithEmailAllowed() {
        return getAttribute(LOGIN_WITH_EMAIL_ALLOWED, false);
    }

    @Override
    public void setLoginWithEmailAllowed(boolean loginWithEmailAllowed) {
        setAttribute(LOGIN_WITH_EMAIL_ALLOWED, loginWithEmailAllowed);
    }

    @Override
    public boolean isDuplicateEmailsAllowed() {
        return getAttribute(IS_DUPLICATE_EMAILS_ALLOWED, false);
    }

    @Override
    public void setDuplicateEmailsAllowed(boolean duplicateEmailsAllowed) {
        setAttribute(IS_DUPLICATE_EMAILS_ALLOWED, duplicateEmailsAllowed);
    }

    @Override
    public boolean isResetPasswordAllowed() {
        return getAttribute(IS_RESET_PASSWORD_ALLOWED, false);
    }

    @Override
    public void setResetPasswordAllowed(boolean resetPasswordAllowed) {
        setAttribute(IS_DUPLICATE_EMAILS_ALLOWED, resetPasswordAllowed);
    }

    @Override
    public String getDefaultSignatureAlgorithm() {
        return getAttribute(DEFAULT_SIG_ALGORITHM);
    }

    @Override
    public void setDefaultSignatureAlgorithm(String defaultSignatureAlgorithm) {
        setAttribute(DEFAULT_SIG_ALGORITHM, defaultSignatureAlgorithm);
    }

    @Override
    public boolean isRevokeRefreshToken() {
        return getAttribute(IS_REVOKE_REFRESH_TOKEN, false);
    }

    @Override
    public void setRevokeRefreshToken(boolean revokeRefreshToken) {
        setAttribute(IS_REVOKE_REFRESH_TOKEN, revokeRefreshToken);
    }

    @Override
    public int getRefreshTokenMaxReuse() {
        return getAttribute(REFRESH_TOKEN_MAX_REUSE, 0);
    }

    @Override
    public void setRefreshTokenMaxReuse(int revokeRefreshTokenCount) {
        setAttribute(REFRESH_TOKEN_MAX_REUSE, revokeRefreshTokenCount);
    }

    @Override
    public int getSsoSessionIdleTimeout() {
        return getAttribute(SSO_SESSION_IDLE_TIMEOUT, 0);
    }

    @Override
    public void setSsoSessionIdleTimeout(int seconds) {
        setAttribute(SSO_SESSION_IDLE_TIMEOUT, seconds);
    }

    @Override
    public int getSsoSessionMaxLifespan() {
        return getAttribute(SSO_SESSION_MAX_LIFESPAN, 0);
    }

    @Override
    public void setSsoSessionMaxLifespan(int seconds) {
        setAttribute(SSO_SESSION_MAX_LIFESPAN, seconds);
    }

    @Override
    public int getSsoSessionIdleTimeoutRememberMe() {
        return getAttribute(SSO_SESSION_IDLE_TIMEOUT_REMEMBER_ME, 0);
    }

    @Override
    public void setSsoSessionIdleTimeoutRememberMe(int seconds) {
        setAttribute(SSO_SESSION_IDLE_TIMEOUT_REMEMBER_ME, seconds);
    }

    @Override
    public int getSsoSessionMaxLifespanRememberMe() {
        return getAttribute(SSO_SESSION_MAX_LIFESPAN_REMEMBER_ME, 0);
    }

    @Override
    public void setSsoSessionMaxLifespanRememberMe(int seconds) {
        setAttribute(SSO_SESSION_MAX_LIFESPAN_REMEMBER_ME, seconds);
    }

    @Override
    public int getOfflineSessionIdleTimeout() {
        return getAttribute(OFFLINE_SESSION_IDLE_TIMEOUT, 0);
    }

    @Override
    public void setOfflineSessionIdleTimeout(int seconds) {
        setAttribute(OFFLINE_SESSION_IDLE_TIMEOUT, seconds);
    }

    @Override
    public int getAccessTokenLifespan() {
        return getAttribute(ACCESS_TOKEN_LIFESPAN, 0);
    }

    @Override
    public void setAccessTokenLifespan(int seconds) {
        setAttribute(ACCESS_TOKEN_LIFESPAN, seconds);
    }

    @Override
    public boolean isOfflineSessionMaxLifespanEnabled() {
        return getAttribute(IS_OFFLINE_SESSION_MAX_LIFESPAN_ENABLED, false);
    }

    @Override
    public void setOfflineSessionMaxLifespanEnabled(boolean offlineSessionMaxLifespanEnabled) {
        setAttribute(IS_OFFLINE_SESSION_MAX_LIFESPAN_ENABLED, offlineSessionMaxLifespanEnabled);
    }

    @Override
    public int getOfflineSessionMaxLifespan() {
        return getAttribute(OFFLINE_SESSION_MAX_LIFESPAN, 0);
    }

    @Override
    public void setOfflineSessionMaxLifespan(int seconds) {
        setAttribute(OFFLINE_SESSION_MAX_LIFESPAN, seconds);
    }

    @Override
    public int getClientSessionIdleTimeout() {
        return getAttribute(CLIENT_SESSION_IDLE_TIMEOUT, 0);
    }

    @Override
    public void setClientSessionIdleTimeout(int seconds) {
        setAttribute(CLIENT_SESSION_IDLE_TIMEOUT, seconds);
    }

    @Override
    public int getClientSessionMaxLifespan() {
        return getAttribute(CLIENT_SESSION_MAX_LIFESPAN, 0);
    }

    @Override
    public void setClientSessionMaxLifespan(int seconds) {
        setAttribute(CLIENT_SESSION_MAX_LIFESPAN, seconds);
    }

    @Override
    public int getClientOfflineSessionIdleTimeout() {
        return getAttribute(CLIENT_OFFLINE_SESSION_IDLE_TIMEOUT, 0);
    }

    @Override
    public void setClientOfflineSessionIdleTimeout(int seconds) {
        setAttribute(CLIENT_OFFLINE_SESSION_IDLE_TIMEOUT, seconds);
    }

    @Override
    public int getClientOfflineSessionMaxLifespan() {
        return getAttribute(CLIENT_OFFLINE_SESSION_MAX_LIFESPAN, 0);
    }

    @Override
    public void setClientOfflineSessionMaxLifespan(int seconds) {
        setAttribute(CLIENT_OFFLINE_SESSION_MAX_LIFESPAN, seconds);
    }

    @Override
    public int getAccessTokenLifespanForImplicitFlow() {
        return getAttribute(ACCESS_TOKEN_LIFESPAN_FOR_IMPLICIT_FLOW, 0);
    }

    @Override
    public void setAccessTokenLifespanForImplicitFlow(int seconds) {
        setAttribute(ACCESS_TOKEN_LIFESPAN_FOR_IMPLICIT_FLOW, seconds);
    }

    @Override
    public int getAccessCodeLifespan() {
        return getAttribute(ACCESS_CODE_LIFESPAN, 0);
    }

    @Override
    public void setAccessCodeLifespan(int seconds) {
        setAttribute(ACCESS_CODE_LIFESPAN, seconds);
    }

    @Override
    public int getAccessCodeLifespanUserAction() {
        return getAttribute(ACCESS_CODE_LIFESPAN_USER_ACTION, 0);
    }

    @Override
    public void setAccessCodeLifespanUserAction(int seconds) {
        setAttribute(ACCESS_CODE_LIFESPAN_USER_ACTION, seconds);
    }

    @Override
    public int getAccessCodeLifespanLogin() {
        return getAttribute(ACCESS_CODE_LIFESPAN_LOGIN, 0);
    }

    @Override
    public void setAccessCodeLifespanLogin(int seconds) {
        setAttribute(ACCESS_CODE_LIFESPAN_LOGIN, seconds);
    }

    @Override
    public int getActionTokenGeneratedByAdminLifespan() {
        return getAttribute(ACTION_TOKEN_GENERATED_BY_ADMIN_LIFESPAN, 0);
    }

    @Override
    public void setActionTokenGeneratedByAdminLifespan(int seconds) {
        setAttribute(ACTION_TOKEN_GENERATED_BY_ADMIN_LIFESPAN, seconds);
    }

    @Override
    public int getActionTokenGeneratedByUserLifespan() {
        return getAttribute(ACTION_TOKEN_GENERATED_BY_USER_LIFESPAN, getAccessCodeLifespanUserAction());
    }

    @Override
    public void setActionTokenGeneratedByUserLifespan(int seconds) {
        setAttribute(ACTION_TOKEN_GENERATED_BY_USER_LIFESPAN, seconds);
    }

    @Override
    public int getActionTokenGeneratedByUserLifespan(String actionTokenType) {
        if (actionTokenType == null || getAttribute(ACTION_TOKEN_GENERATED_BY_USER_LIFESPAN + "." + actionTokenType) == null) {
            return getActionTokenGeneratedByUserLifespan();
        }
        return getAttribute(ACTION_TOKEN_GENERATED_BY_USER_LIFESPAN + "." + actionTokenType, getAccessCodeLifespanUserAction());
    }

    @Override
    public void setActionTokenGeneratedByUserLifespan(String actionTokenType, Integer seconds) {
        if (actionTokenType != null && !actionTokenType.isEmpty() && seconds != null) {
            setAttribute(ACTION_TOKEN_GENERATED_BY_USER_LIFESPAN + "." + actionTokenType, seconds);
        }
    }

    @Override
    public Map<String, Integer> getUserActionTokenLifespans() {
        Map<String, Set<String>> attrs = realmEntity.getAttributes();
        if (attrs == null || attrs.isEmpty()) return Collections.emptyMap();

        Map<String, Integer> tokenLifespans = attrs.entrySet().stream()
            .filter(Objects::nonNull)
            .filter(entry -> nonNull(entry.getValue()) && !entry.getValue().isEmpty())
            .filter(entry -> entry.getKey().startsWith(ACTION_TOKEN_GENERATED_BY_USER_LIFESPAN + "."))
            .collect(Collectors.toMap(
                entry -> entry.getKey().substring(ACTION_TOKEN_GENERATED_BY_USER_LIFESPAN.length() + 1),
                entry -> Integer.valueOf(entry.getValue().iterator().next())));

        return Collections.unmodifiableMap(tokenLifespans);
    }

    @Override
    public PasswordPolicy getPasswordPolicy() {
        return PasswordPolicy.parse(session, getAttribute(PASSWORD_POLICY));
    }

    @Override
    public void setPasswordPolicy(PasswordPolicy policy) {
        setAttribute(PASSWORD_POLICY, policy.toString());
    }

    @Override
    public OAuth2DeviceConfig getOAuth2DeviceConfig() {
        return new OAuth2DeviceConfig(this);
    }

    @Override
    public CibaConfig getCibaPolicy() {
        return new CibaConfig(this);
    }

    @Override
    public ParConfig getParPolicy() {
        return new ParConfig(this);
    }

    @Override
    public Stream<RequiredCredentialModel> getRequiredCredentialsStream() {
        return getDeserializedAttributes(REQUIRED_CREDENTIALS, RequiredCredentialModel.class).stream();
    }

    @Override
    public void addRequiredCredential(String cred) {
        RequiredCredentialModel model = RequiredCredentialModel.BUILT_IN.get(cred);
        if (model == null) {
            throw new RuntimeException("Unknown credential type " + cred);
        }

        List<RequiredCredentialModel> requiredCredentials = getRequiredCredentialsStream().collect(Collectors.toList());
        if (requiredCredentials.stream().anyMatch(credential -> Objects.equals(model.getType(), credential.getType()))) {
            throw new ModelDuplicateException("A Required Credential with given type already exists.");
        }

        requiredCredentials.add(model);
        setSerializedAttributeValues(REQUIRED_CREDENTIALS, requiredCredentials);
    }

    @Override
    public void updateRequiredCredentials(Set<String> creds) {
        List<RequiredCredentialModel> result = getRequiredCredentialsStream().collect(Collectors.toList());

        Set<RequiredCredentialModel> currentRequiredCredentials = getRequiredCredentialsStream().collect(Collectors.toSet());
        Consumer<RequiredCredentialModel> updateCredentialFnc = e -> {
            Optional<RequiredCredentialModel> existingEntity = currentRequiredCredentials.stream()
                .filter(existing -> Objects.equals(e.getType(), existing.getType()))
                .findFirst();

            if (existingEntity.isPresent()) {
                updateRequiredCredential(existingEntity.get(), e);
            } else {
                result.add(e);
            }
        };

        creds.stream()
            .map(RequiredCredentialModel.BUILT_IN::get)
            .peek(c -> {
                if (c == null) throw new RuntimeException("Unknown credential type " + c.getType());
            })
            .forEach(updateCredentialFnc);
    }

    private void updateRequiredCredential(RequiredCredentialModel existing, RequiredCredentialModel newValue) {
        existing.setFormLabel(newValue.getFormLabel());
        existing.setInput(newValue.isInput());
        existing.setSecret(newValue.isSecret());
    }

    @Override
    public OTPPolicy getOTPPolicy() {
        OTPPolicy otpPolicy = getDeserializedAttribute(OTP_POLICY, OTPPolicy.class);
        return otpPolicy == null ? OTPPolicy.DEFAULT_POLICY : otpPolicy;
    }

    @Override
    public void setOTPPolicy(OTPPolicy policy) {
        setSerializedAttributeValue(OTP_POLICY, policy);
    }

    @Override
    public WebAuthnPolicy getWebAuthnPolicy() {
        WebAuthnPolicy webAuthnPolicy = getDeserializedAttribute(WEB_AUTHN_POLICY, WebAuthnPolicy.class);
        return webAuthnPolicy == null ? WebAuthnPolicy.DEFAULT_POLICY : webAuthnPolicy;
    }

    @Override
    public void setWebAuthnPolicy(WebAuthnPolicy policy) {
        setSerializedAttributeValue(WEB_AUTHN_POLICY, policy);
    }

    @Override
    public WebAuthnPolicy getWebAuthnPolicyPasswordless() {
        WebAuthnPolicy webAuthnPolicy = getDeserializedAttribute(WEB_AUTHN_POLICY_PASSWORDLESS, WebAuthnPolicy.class);
        return webAuthnPolicy == null ? WebAuthnPolicy.DEFAULT_POLICY : webAuthnPolicy;
    }

    @Override
    public void setWebAuthnPolicyPasswordless(WebAuthnPolicy policy) {
        setSerializedAttributeValue(WEB_AUTHN_POLICY_PASSWORDLESS, policy);
    }

    @Override
    public Map<String, String> getBrowserSecurityHeaders() {
        Map<String, String> securityHeaders = getDeserializedAttribute(BROWSER_SECURITY_HEADERS, new TypeReference<>() {
        });

        return securityHeaders == null ? Collections.emptyMap() : securityHeaders;
    }

    @Override
    public void setBrowserSecurityHeaders(Map<String, String> headers) {
        setSerializedAttributeValue(BROWSER_SECURITY_HEADERS, headers);
    }

    @Override
    public Map<String, String> getSmtpConfig() {
        Map<String, String> smtpConfig = getDeserializedAttribute(SMTP_CONFIG, new TypeReference<>() {
        });

        return smtpConfig == null ? Collections.emptyMap() : smtpConfig;
    }

    @Override
    public void setSmtpConfig(Map<String, String> smtpConfig) {
        setSerializedAttributeValue(SMTP_CONFIG, smtpConfig);
    }

    @Override
    public AuthenticationFlowModel getBrowserFlow() {
        return getAuthenticationFlowById(getAttribute(BROWSER_FLOW));
    }

    @Override
    public void setBrowserFlow(AuthenticationFlowModel flow) {
        setAttribute(BROWSER_FLOW, flow.getId());
    }

    @Override
    public AuthenticationFlowModel getRegistrationFlow() {
        return getAuthenticationFlowById(getAttribute(REGISTRATION_FLOW));
    }

    @Override
    public void setRegistrationFlow(AuthenticationFlowModel flow) {
        setAttribute(REGISTRATION_FLOW, flow.getId());
    }

    @Override
    public AuthenticationFlowModel getDirectGrantFlow() {
        return getAuthenticationFlowById(getAttribute(DIRECT_GRANT_FLOW));
    }

    @Override
    public void setDirectGrantFlow(AuthenticationFlowModel flow) {
        setAttribute(DIRECT_GRANT_FLOW, flow.getId());
    }

    @Override
    public AuthenticationFlowModel getResetCredentialsFlow() {
        return getAuthenticationFlowById(getAttribute(RESET_CREDENTIALS_FLOW));
    }

    @Override
    public void setResetCredentialsFlow(AuthenticationFlowModel flow) {
        setAttribute(RESET_CREDENTIALS_FLOW, flow.getId());
    }

    @Override
    public AuthenticationFlowModel getClientAuthenticationFlow() {
        return getAuthenticationFlowById(getAttribute(CLIENT_AUTHENTICATION_FLOW));
    }

    @Override
    public void setClientAuthenticationFlow(AuthenticationFlowModel flow) {
        setAttribute(CLIENT_AUTHENTICATION_FLOW, flow.getId());
    }

    @Override
    public AuthenticationFlowModel getDockerAuthenticationFlow() {
        return getAuthenticationFlowById(getAttribute(DOCKER_AUTHENTICATION_FLOW));
    }

    @Override
    public void setDockerAuthenticationFlow(AuthenticationFlowModel flow) {
        setAttribute(DOCKER_AUTHENTICATION_FLOW, flow.getId());
    }

    @Override
    public Stream<AuthenticationFlowModel> getAuthenticationFlowsStream() {
        return getDeserializedAttributes(AUTHENTICATION_FLOWS, AuthenticationFlowModel.class).stream();
    }

    @Override
    public AuthenticationFlowModel getFlowByAlias(String alias) {
        return getDeserializedAttributes(AUTHENTICATION_FLOWS, AuthenticationFlowModel.class).stream()
            .filter(f -> Objects.equals(f.getAlias(), alias))
            .findFirst()
            .orElse(null);
    }

    @Override
    public AuthenticationFlowModel addAuthenticationFlow(AuthenticationFlowModel model) {
        AuthenticationFlowModel existingFlow = getAuthenticationFlowById(model.getId());
        if (existingFlow != null) {
            throw new ModelDuplicateException("An AuthenticationFlow with given id already exists");
        }

        List<AuthenticationFlowModel> existingFlows = getDeserializedAttributes(AUTHENTICATION_FLOWS, AuthenticationFlowModel.class);
        existingFlows.add(model);

        if (model.getId() == null) {
            model.setId(KeycloakModelUtils.generateId());
        }

        setSerializedAttributeValues(AUTHENTICATION_FLOWS, existingFlows);

        return model;
    }

    @Override
    public AuthenticationFlowModel getAuthenticationFlowById(String id) {
        return getDeserializedAttributes(AUTHENTICATION_FLOWS, AuthenticationFlowModel.class).stream().filter(f -> f.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public void removeAuthenticationFlow(AuthenticationFlowModel model) {
        List<AuthenticationFlowModel> flowsWithoutModel = getDeserializedAttributes(AUTHENTICATION_FLOWS, AuthenticationFlowModel.class).stream()
            .filter(f -> !f.getId().equals(model.getId()))
            .collect(Collectors.toList());

        setSerializedAttributeValues(AUTHENTICATION_FLOWS, flowsWithoutModel);
    }

    @Override
    public void updateAuthenticationFlow(AuthenticationFlowModel model) {
        List<AuthenticationFlowModel> flowsWithoutModel = getDeserializedAttributes(AUTHENTICATION_FLOWS, AuthenticationFlowModel.class).stream()
            .filter(f -> !f.getId().equals(model.getId()))
            .collect(Collectors.toList());

        flowsWithoutModel.add(model);
        setSerializedAttributeValues(AUTHENTICATION_FLOWS, flowsWithoutModel);
    }

    @Override
    public Stream<AuthenticationExecutionModel> getAuthenticationExecutionsStream(String flowId) {
        return getDeserializedAttributes(AUTHENTICATION_EXECUTION_MODELS, AuthenticationExecutionModel.class).stream()
            .filter(e -> Objects.equals(e.getParentFlow(), flowId))
            .sorted(AuthenticationExecutionModel.ExecutionComparator.SINGLETON);
    }

    @Override
    public AuthenticationExecutionModel getAuthenticationExecutionById(String id) {
        return getDeserializedAttributes(AUTHENTICATION_EXECUTION_MODELS, AuthenticationExecutionModel.class).stream()
            .filter(e -> e.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    @Override
    public AuthenticationExecutionModel getAuthenticationExecutionByFlowId(String flowId) {
        return getDeserializedAttributes(AUTHENTICATION_EXECUTION_MODELS, AuthenticationExecutionModel.class).stream()
            .filter(e -> Objects.equals(e.getFlowId(), flowId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public AuthenticationExecutionModel addAuthenticatorExecution(AuthenticationExecutionModel model) {
        AuthenticationExecutionModel existingExecution = getAuthenticationExecutionById(model.getId());
        if (existingExecution != null) {
            throw new ModelDuplicateException("An RequiredActionProvider with given id already exists");
        }

        if (model.getId() == null) {
            model.setId(KeycloakModelUtils.generateId());
        }

        List<AuthenticationExecutionModel> values = getDeserializedAttributes(AUTHENTICATION_EXECUTION_MODELS, AuthenticationExecutionModel.class);
        values.add(model);
        setSerializedAttributeValues(AUTHENTICATION_EXECUTION_MODELS, values);

        return model;
    }

    @Override
    public void updateAuthenticatorExecution(AuthenticationExecutionModel model) {
        List<AuthenticationExecutionModel> executionsWithoutModel = getDeserializedAttributes(AUTHENTICATION_EXECUTION_MODELS, AuthenticationExecutionModel.class).stream()
            .filter(e -> !e.getId().equals(model.getId()))
            .collect(Collectors.toList());

        executionsWithoutModel.add(model);
        setSerializedAttributeValues(AUTHENTICATION_EXECUTION_MODELS, executionsWithoutModel);
    }

    @Override
    public void removeAuthenticatorExecution(AuthenticationExecutionModel model) {
        List<AuthenticationExecutionModel> executionsWithoutModel = getDeserializedAttributes(AUTHENTICATION_EXECUTION_MODELS, AuthenticationExecutionModel.class).stream()
            .filter(e -> !e.getId().equals(model.getId()))
            .collect(Collectors.toList());

        setSerializedAttributeValues(AUTHENTICATION_EXECUTION_MODELS, executionsWithoutModel);
    }

    @Override
    public Stream<AuthenticatorConfigModel> getAuthenticatorConfigsStream() {
        return getDeserializedAttributes(AUTHENTICATOR_CONFIG_MODELS, AuthenticatorConfigModel.class).stream();
    }

    @Override
    public AuthenticatorConfigModel addAuthenticatorConfig(AuthenticatorConfigModel model) {
        AuthenticatorConfigModel existing = getAuthenticatorConfigById(model.getId());
        if (existing != null) {
            throw new ModelDuplicateException("An Authenticator Config with given id already exists.");
        }

        if (model.getId() == null) {
            model.setId(KeycloakModelUtils.generateId());
        }

        List<AuthenticatorConfigModel> values = getDeserializedAttributes(AUTHENTICATOR_CONFIG_MODELS, AuthenticatorConfigModel.class);
        values.add(model);
        setSerializedAttributeValues(AUTHENTICATOR_CONFIG_MODELS, values);

        return model;
    }

    @Override
    public void updateAuthenticatorConfig(AuthenticatorConfigModel model) {
        List<AuthenticatorConfigModel> withoutModel = getDeserializedAttributes(AUTHENTICATOR_CONFIG_MODELS, AuthenticatorConfigModel.class).stream()
            .filter(e -> !e.getId().equals(model.getId()))
            .collect(Collectors.toList());

        withoutModel.add(model);
        setSerializedAttributeValues(AUTHENTICATOR_CONFIG_MODELS, withoutModel);
    }

    @Override
    public void removeAuthenticatorConfig(AuthenticatorConfigModel model) {
        List<AuthenticatorConfigModel> withoutModel = getDeserializedAttributes(AUTHENTICATOR_CONFIG_MODELS, AuthenticatorConfigModel.class).stream()
            .filter(e -> !e.getId().equals(model.getId()))
            .collect(Collectors.toList());

        setSerializedAttributeValues(AUTHENTICATOR_CONFIG_MODELS, withoutModel);
    }

    @Override
    public AuthenticatorConfigModel getAuthenticatorConfigById(String id) {
        return getDeserializedAttributes(AUTHENTICATOR_CONFIG_MODELS, AuthenticatorConfigModel.class).stream()
            .filter(e -> e.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    @Override
    public AuthenticatorConfigModel getAuthenticatorConfigByAlias(String alias) {
        return getDeserializedAttributes(AUTHENTICATOR_CONFIG_MODELS, AuthenticatorConfigModel.class).stream()
            .filter(e -> Objects.equals(e.getAlias(), alias))
            .findFirst()
            .orElse(null);
    }

    @Override
    public Stream<RequiredActionProviderModel> getRequiredActionProvidersStream() {
        return getDeserializedAttributes(REQUIRED_ACTION_PROVIDER_MODELS, RequiredActionProviderModel.class).stream()
            .sorted(RequiredActionProviderModel.RequiredActionComparator.SINGLETON);
    }

    @Override
    public RequiredActionProviderModel addRequiredActionProvider(RequiredActionProviderModel model) {
        if (getRequiredActionProviderById(model.getId()) != null) {
            throw new ModelDuplicateException("A Required Action Provider with given id already exists.");
        }
        if (getRequiredActionProviderByAlias(model.getAlias()) != null) {
            throw new ModelDuplicateException("A Required Action Provider with given alias already exists.");
        }

        if (model.getId() == null) {
            model.setId(KeycloakModelUtils.generateId());
        }

        List<RequiredActionProviderModel> values = getDeserializedAttributes(REQUIRED_ACTION_PROVIDER_MODELS, RequiredActionProviderModel.class);
        values.add(model);
        setSerializedAttributeValues(REQUIRED_ACTION_PROVIDER_MODELS, values);

        return model;
    }

    @Override
    public void updateRequiredActionProvider(RequiredActionProviderModel model) {
        List<RequiredActionProviderModel> withoutModel = getDeserializedAttributes(REQUIRED_ACTION_PROVIDER_MODELS, RequiredActionProviderModel.class).stream()
            .filter(e -> !e.getId().equals(model.getId()))
            .collect(Collectors.toList());

        withoutModel.add(model);
        setSerializedAttributeValues(REQUIRED_ACTION_PROVIDER_MODELS, withoutModel);
    }

    @Override
    public void removeRequiredActionProvider(RequiredActionProviderModel model) {
        List<RequiredActionProviderModel> withoutModel = getDeserializedAttributes(REQUIRED_ACTION_PROVIDER_MODELS, RequiredActionProviderModel.class).stream()
            .filter(e -> !e.getId().equals(model.getId()))
            .collect(Collectors.toList());

        setSerializedAttributeValues(REQUIRED_ACTION_PROVIDER_MODELS, withoutModel);
    }

    @Override
    public RequiredActionProviderModel getRequiredActionProviderById(String id) {
        return getDeserializedAttributes(REQUIRED_ACTION_PROVIDER_MODELS, RequiredActionProviderModel.class).stream()
            .filter(e -> e.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    @Override
    public RequiredActionProviderModel getRequiredActionProviderByAlias(String alias) {
        return getDeserializedAttributes(REQUIRED_ACTION_PROVIDER_MODELS, RequiredActionProviderModel.class).stream()
            .filter(e -> Objects.equals(e.getAlias(), alias))
            .findFirst()
            .orElse(null);
    }

    @Override
    public Stream<IdentityProviderModel> getIdentityProvidersStream() {
        return getDeserializedAttributes(IDENTITY_PROVIDERS, IdentityProviderModel.class).stream();
    }

    @Override
    public IdentityProviderModel getIdentityProviderByAlias(String alias) {
        return getDeserializedAttributes(IDENTITY_PROVIDERS, IdentityProviderModel.class).stream()
            .filter(e -> Objects.equals(e.getAlias(), alias))
            .findFirst()
            .orElse(null);
    }

    @Override
    public void addIdentityProvider(IdentityProviderModel model) {
        if (getIdentityProviderByAlias(model.getAlias()) != null) {
            throw new ModelDuplicateException("An Identity Provider with given alias already exists.");
        }

        if (model.getInternalId() == null) {
            model.setInternalId(KeycloakModelUtils.generateId());
        }

        List<IdentityProviderModel> values = getDeserializedAttributes(IDENTITY_PROVIDERS, IdentityProviderModel.class);
        values.add(model);
        setSerializedAttributeValues(IDENTITY_PROVIDERS, values);
    }

    @Override
    public void removeIdentityProviderByAlias(String alias) {
        List<IdentityProviderModel> allProviders = getDeserializedAttributes(IDENTITY_PROVIDERS, IdentityProviderModel.class);
        IdentityProviderModel model = allProviders.stream()
            .filter(e -> Objects.equals(e.getAlias(), alias))
            .findFirst()
            .orElse(null);

        if (model != null) {
            allProviders.remove(model);
            setSerializedAttributeValues(IDENTITY_PROVIDERS, allProviders);

            session.getKeycloakSessionFactory().publish(new RealmModel.IdentityProviderRemovedEvent() {

                @Override
                public RealmModel getRealm() {
                    return CassandraRealmAdapter.this;
                }

                @Override
                public IdentityProviderModel getRemovedIdentityProvider() {
                    return model;
                }

                @Override
                public KeycloakSession getKeycloakSession() {
                    return session;
                }
            });
        }
    }

    @Override
    public void updateIdentityProvider(IdentityProviderModel identityProvider) {
        List<IdentityProviderModel> allProviders = getDeserializedAttributes(IDENTITY_PROVIDERS, IdentityProviderModel.class);
        allProviders.stream()
            .filter(e -> e.getInternalId().equals(identityProvider.getInternalId()))
            .findFirst()
            .ifPresent(oldPS -> {
                oldPS.setAlias(identityProvider.getAlias());
                oldPS.setDisplayName(identityProvider.getDisplayName());
                oldPS.setProviderId(identityProvider.getProviderId());
                oldPS.setFirstBrokerLoginFlowId(identityProvider.getFirstBrokerLoginFlowId());
                oldPS.setPostBrokerLoginFlowId(identityProvider.getPostBrokerLoginFlowId());
                oldPS.setEnabled(identityProvider.isEnabled());
                oldPS.setTrustEmail(identityProvider.isTrustEmail());
                oldPS.setStoreToken(identityProvider.isStoreToken());
                oldPS.setLinkOnly(identityProvider.isLinkOnly());
                oldPS.setAddReadTokenRoleOnCreate(identityProvider.isAddReadTokenRoleOnCreate());
                oldPS.setAuthenticateByDefault(identityProvider.isAuthenticateByDefault());
                oldPS.setConfig(identityProvider.getConfig() == null ? null : new HashMap<>(identityProvider.getConfig()));
            });

        setSerializedAttributeValues(IDENTITY_PROVIDERS, allProviders);

        session.getKeycloakSessionFactory().publish(new RealmModel.IdentityProviderUpdatedEvent() {

            @Override
            public RealmModel getRealm() {
                return CassandraRealmAdapter.this;
            }

            @Override
            public IdentityProviderModel getUpdatedIdentityProvider() {
                return identityProvider;
            }

            @Override
            public KeycloakSession getKeycloakSession() {
                return session;
            }
        });
    }

    @Override
    public Stream<IdentityProviderMapperModel> getIdentityProviderMappersStream() {
        return getDeserializedAttributes(IDENTITY_PROVIDER_MAPPERS, IdentityProviderMapperModel.class).stream();
    }

    @Override
    public Stream<IdentityProviderMapperModel> getIdentityProviderMappersByAliasStream(String brokerAlias) {
        return getDeserializedAttributes(IDENTITY_PROVIDER_MAPPERS, IdentityProviderMapperModel.class).stream()
            .filter(e -> Objects.equals(e.getIdentityProviderAlias(), brokerAlias));
    }

    @Override
    public IdentityProviderMapperModel addIdentityProviderMapper(IdentityProviderMapperModel model) {
        if (getIdentityProviderMapperById(model.getId()) != null) {
            throw new ModelDuplicateException("An IdentityProviderMapper with given id already exists");
        }

        if (model.getId() == null) {
            model.setId(KeycloakModelUtils.generateId());
        }

        List<IdentityProviderMapperModel> values = getDeserializedAttributes(IDENTITY_PROVIDER_MAPPERS, IdentityProviderMapperModel.class);
        values.add(model);
        setSerializedAttributeValues(IDENTITY_PROVIDER_MAPPERS, values);

        return model;
    }

    @Override
    public void removeIdentityProviderMapper(IdentityProviderMapperModel mapping) {
        List<IdentityProviderMapperModel> withoutModel = getDeserializedAttributes(IDENTITY_PROVIDER_MAPPERS, IdentityProviderMapperModel.class).stream()
            .filter(e -> !e.getId().equals(mapping.getId()))
            .collect(Collectors.toList());

        setSerializedAttributeValues(IDENTITY_PROVIDER_MAPPERS, withoutModel);
    }

    @Override
    public void updateIdentityProviderMapper(IdentityProviderMapperModel mapping) {
        List<IdentityProviderMapperModel> withoutModel = getDeserializedAttributes(IDENTITY_PROVIDER_MAPPERS, IdentityProviderMapperModel.class).stream()
            .filter(e -> !e.getId().equals(mapping.getId()))
            .collect(Collectors.toList());

        withoutModel.add(mapping);
        setSerializedAttributeValues(IDENTITY_PROVIDER_MAPPERS, withoutModel);
    }

    @Override
    public IdentityProviderMapperModel getIdentityProviderMapperById(String id) {
        return getDeserializedAttributes(IDENTITY_PROVIDER_MAPPERS, IdentityProviderMapperModel.class).stream()
            .filter(e -> e.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    @Override
    public IdentityProviderMapperModel getIdentityProviderMapperByName(String brokerAlias, String name) {
        return getDeserializedAttributes(IDENTITY_PROVIDER_MAPPERS, IdentityProviderMapperModel.class).stream()
            .filter(e -> Objects.equals(e.getIdentityProviderAlias(), brokerAlias) && Objects.equals(e.getName(), name))
            .findFirst()
            .orElse(null);
    }

    @Override
    public ComponentModel addComponentModel(ComponentModel model) {
        model = importComponentModel(model);
        ComponentUtil.notifyCreated(session, this, model);
        return model;
    }

    @Override
    public ComponentModel importComponentModel(ComponentModel model) {
        try {
            ComponentFactory componentFactory = ComponentUtil.getComponentFactory(session, model);
            if (componentFactory == null && System.getProperty(COMPONENT_PROVIDER_EXISTS_DISABLED) == null) {
                throw new IllegalArgumentException("Invalid component type");
            }
            componentFactory.validateConfiguration(session, this, model);
        } catch (IllegalArgumentException | ComponentValidationException e) {
            if (System.getProperty(COMPONENT_PROVIDER_EXISTS_DISABLED) == null) {
                throw e;
            }
        }

        if (getComponent(model.getId()) != null) {
            throw new ModelDuplicateException("A Component with given id already exists");
        }

        if (model.getId() == null) {
            model.setId(KeycloakModelUtils.generateId());
        }

        List<ComponentModel> values = getDeserializedAttributes(COMPONENTS, ComponentModel.class);
        values.add(model);
        setSerializedAttributeValues(COMPONENTS, values);

        return model;
    }

    @Override
    public void updateComponent(ComponentModel component) {
        ComponentUtil.getComponentFactory(session, component).validateConfiguration(session, this, component);

        ComponentModel oldModel = getComponent(component.getId());
        List<ComponentModel> withoutModel = getDeserializedAttributes(COMPONENTS, ComponentModel.class).stream()
            .filter(e -> !e.getId().equals(component.getId()))
            .collect(Collectors.toList());

        withoutModel.add(component);
        setSerializedAttributeValues(COMPONENTS, withoutModel);

        ComponentUtil.notifyUpdated(session, this, oldModel, component);
    }

    @Override
    public void removeComponent(ComponentModel component) {
        if (getComponent(component.getId()) == null) return;

        session.users().preRemove(this, component);
        ComponentUtil.notifyPreRemove(session, this, component);
        removeComponents(component.getId());

        List<ComponentModel> withoutModel = getDeserializedAttributes(COMPONENTS, ComponentModel.class).stream()
            .filter(e -> !e.getId().equals(component.getId()))
            .collect(Collectors.toList());

        setSerializedAttributeValues(COMPONENTS, withoutModel);
    }

    @Override
    public void removeComponents(String parentId) {
        getDeserializedAttributes(COMPONENTS, ComponentModel.class).stream()
            .filter(e -> Objects.equals(parentId, e.getParentId()))
            .forEach(c -> {
                session.users().preRemove(this, c);
                ComponentUtil.notifyPreRemove(session, this, c);
            });

        List<ComponentModel> withoutModel = getDeserializedAttributes(COMPONENTS, ComponentModel.class).stream()
            .filter(e -> !Objects.equals(parentId, e.getParentId()))
            .collect(Collectors.toList());

        setSerializedAttributeValues(COMPONENTS, withoutModel);
    }

    @Override
    public Stream<ComponentModel> getComponentsStream(String parentId, String providerType) {
        return getDeserializedAttributes(COMPONENTS, ComponentModel.class).stream()
            .filter(e -> Objects.equals(parentId, e.getParentId()))
            .filter(e -> Objects.equals(providerType, e.getProviderType()));
    }

    @Override
    public Stream<ComponentModel> getComponentsStream(String parentId) {
        return getDeserializedAttributes(COMPONENTS, ComponentModel.class).stream()
            .filter(e -> Objects.equals(parentId, e.getParentId()));
    }

    @Override
    public Stream<ComponentModel> getComponentsStream() {
        return getDeserializedAttributes(COMPONENTS, ComponentModel.class).stream();
    }

    @Override
    public ComponentModel getComponent(String id) {
        return getDeserializedAttributes(COMPONENTS, ComponentModel.class).stream()
            .filter(e -> e.getId().equals(id))
            .findFirst()
            .orElse(null);
    }

    @Override
    public String getLoginTheme() {
        return getAttribute(LOGIN_THEME);
    }

    @Override
    public void setLoginTheme(String name) {
        setAttribute(LOGIN_THEME, name);
    }

    @Override
    public String getAccountTheme() {
        return getAttribute(ACCOUNT_THEME);
    }

    @Override
    public void setAccountTheme(String name) {
        setAttribute(ACCOUNT_THEME, name);
    }

    @Override
    public String getAdminTheme() {
        return getAttribute(ADMIN_THEME);
    }

    @Override
    public void setAdminTheme(String name) {
        setAttribute(ADMIN_THEME, name);
    }

    @Override
    public String getEmailTheme() {
        return getAttribute(EMAIL_THEME);
    }

    @Override
    public void setEmailTheme(String name) {
        setAttribute(EMAIL_THEME, name);
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
    public boolean isEventsEnabled() {
        return getAttribute(IS_EVENTS_ENABLED, false);
    }

    @Override
    public void setEventsEnabled(boolean enabled) {
        setAttribute(IS_EVENTS_ENABLED, enabled);
    }

    @Override
    public long getEventsExpiration() {
        return getAttribute(EVENTS_EXPIRATION, 0L);
    }

    @Override
    public void setEventsExpiration(long expiration) {
        setAttribute(EVENTS_EXPIRATION, expiration);
    }

    @Override
    public Stream<String> getEventsListenersStream() {
        return getAttributeValues(EVENT_LISTENERS).stream();
    }

    @Override
    public void setEventsListeners(Set<String> listeners) {
        setAttributeValues(EVENT_LISTENERS, new ArrayList<>(listeners));
    }

    @Override
    public Stream<String> getEnabledEventTypesStream() {
        return getAttributeValues(ENABLED_EVENT_TYPES).stream();
    }

    @Override
    public void setEnabledEventTypes(Set<String> enabledEventTypes) {
        setAttributeValues(ENABLED_EVENT_TYPES, new ArrayList<>(enabledEventTypes));
    }

    @Override
    public boolean isAdminEventsEnabled() {
        return getAttribute(IS_ADMIN_EVENTS_ENABLED, false);
    }

    @Override
    public void setAdminEventsEnabled(boolean enabled) {
        setAttribute(IS_ADMIN_EVENTS_ENABLED, enabled);
    }

    @Override
    public boolean isAdminEventsDetailsEnabled() {
        return getAttribute(IS_ADMIN_EVENTS_DETAILS_ENABLED, false);
    }

    @Override
    public void setAdminEventsDetailsEnabled(boolean enabled) {
        setAttribute(IS_ADMIN_EVENTS_DETAILS_ENABLED, enabled);
    }

    @Override
    public ClientModel getMasterAdminClient() {
        String masterAdminClientId = getAttribute(MASTER_ADMIN_CLIENT_ID);
        if (masterAdminClientId == null) {
            return null;
        }

        RealmModel masterRealm = getName().equals(Config.getAdminRealm())
            ? this
            : session.realms().getRealmByName(Config.getAdminRealm());
        return session.clients().getClientById(masterRealm, masterAdminClientId);
    }

    @Override
    public void setMasterAdminClient(ClientModel client) {
        if (client == null) {
            removeAttribute(MASTER_ADMIN_CLIENT_ID);
            return;
        }

        setAttribute(MASTER_ADMIN_CLIENT_ID, client.getId());
    }

    @Override
    public boolean isIdentityFederationEnabled() {
        return getIdentityProvidersStream().findAny().isPresent();
    }

    @Override
    public boolean isInternationalizationEnabled() {
        return getAttribute(IS_INTERNATIONALIZATION_ENABLED, false);
    }

    @Override
    public void setInternationalizationEnabled(boolean enabled) {
        setAttribute(IS_INTERNATIONALIZATION_ENABLED, enabled);
    }

    @Override
    public Stream<String> getSupportedLocalesStream() {
        return getAttributeValues(SUPPORTED_LOCALES).stream();
    }

    @Override
    public void setSupportedLocales(Set<String> locales) {
        setAttributeValues(SUPPORTED_LOCALES, new ArrayList<>(locales));
    }

    @Override
    public String getDefaultLocale() {
        return getAttribute(DEFAULT_LOCALE);
    }

    @Override
    public void setDefaultLocale(String locale) {
        setAttribute(DEFAULT_LOCALE, locale);
    }

    @Override
    public void createOrUpdateRealmLocalizationTexts(String locale, Map<String, String> localizationTexts) {
        Map<String, Map<String, String>> texts = getDeserializedAttribute(LOCALIZATION_TEXTS, new TypeReference<>() {
        });

        if (texts == null) {
            texts = new HashMap<>();
        }

        texts.put(locale, localizationTexts);
        setSerializedAttributeValue(LOCALIZATION_TEXTS, texts);
    }

    @Override
    public boolean removeRealmLocalizationTexts(String locale) {
        Map<String, Map<String, String>> texts = getDeserializedAttribute(LOCALIZATION_TEXTS, new TypeReference<>() {
        });

        if (texts == null || !texts.containsKey(locale)) {
            return false;
        }

        texts.remove(locale);
        setSerializedAttributeValue(LOCALIZATION_TEXTS, texts);
        return true;
    }

    @Override
    public Map<String, Map<String, String>> getRealmLocalizationTexts() {
        Map<String, Map<String, String>> texts = getDeserializedAttribute(LOCALIZATION_TEXTS, new TypeReference<>() {
        });

        if (texts == null) {
            return new HashMap<>();
        }

        return texts;
    }

    @Override
    public Map<String, String> getRealmLocalizationTextsByLocale(String locale) {
        Map<String, Map<String, String>> texts = getDeserializedAttribute(LOCALIZATION_TEXTS, new TypeReference<>() {
        });

        if (texts == null || !texts.containsKey(locale)) {
            return new HashMap<>();
        }

        return texts.get(locale);
    }

    @Override
    public ClientInitialAccessModel createClientInitialAccessModel(int expiration, int count) {
        ClientInitialAccess clientInitialAccess = ClientInitialAccess.builder()
            .id(KeycloakModelUtils.generateId())
            .realmId(realmEntity.getId())
            .timestamp(Time.currentTimeMillis())
            .expiration(expiration == 0 ? null : Time.currentTimeMillis() + TimeAdapter.fromSecondsToMilliseconds(expiration))
            .count(count)
            .build();

        realmRepository.insertOrUpdate(clientInitialAccess);

        return toModel(clientInitialAccess);
    }

    @Override
    public ClientInitialAccessModel getClientInitialAccessModel(String id) {
        ClientInitialAccess clientInitialAccess = realmRepository.getClientInitialAccess(realmEntity.getId(), id);
        if (clientInitialAccess == null) {
            return null;
        }

        return toModel(clientInitialAccess);
    }

    @Override
    public void removeClientInitialAccessModel(String id) {
        realmRepository.deleteClientInitialAccess(realmEntity.getId(), id);
    }

    @Override
    public Stream<ClientInitialAccessModel> getClientInitialAccesses() {
        return realmRepository.getAllClientInitialAccessesByRealmId(realmEntity.getId()).stream()
            .map(this::toModel);
    }

    @Override
    public void decreaseRemainingCount(ClientInitialAccessModel clientInitialAccess) {
        ClientInitialAccess entity = realmRepository.getClientInitialAccess(realmEntity.getId(), clientInitialAccess.getId());
        entity.setCount(entity.getCount() - 1);
        realmRepository.insertOrUpdate(entity);
    }

    // Attributes
    @Override
    public void setAttribute(String name, String value) {
        if (name == null || value == null) {
            return;
        }

        realmEntity.getAttributes().put(name, new HashSet<>(Arrays.asList(value)));
        realmRepository.insertOrUpdate(realmEntity);
    }

    @Override
    public void removeAttribute(String name) {
        if (name == null) {
            return;
        }

        realmEntity.getAttributes().remove(name);
        realmRepository.insertOrUpdate(realmEntity);
    }

    @Override
    public String getAttribute(String name) {
        Set<String> values = realmEntity.getAttribute(name);
        return values.isEmpty() || values.iterator().next().isEmpty() ? null : values.iterator().next();
    }

    @Override
    public Map<String, String> getAttributes() {
        return realmEntity.getAttributes().entrySet().stream()
            .filter(e -> !e.getKey().startsWith(INTERNAL_ATTRIBUTE_PREFIX))
            .filter(e -> e.getValue() != null && !e.getValue().isEmpty() && !e.getValue().iterator().next().isEmpty())
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().iterator().next()));
    }

    public void setAttributeValues(String name, List<String> values) {
        if (name == null || values == null) {
            return;
        }

        realmEntity.getAttributes().put(name, new HashSet<>(values));
        realmRepository.insertOrUpdate(realmEntity);
    }

    private List<String> getAttributeValues(String name) {
        Set<String> values = realmEntity.getAttribute(name);
        return values.stream().filter(v -> v != null && !v.isEmpty()).collect(Collectors.toList());
    }

    private <T> void setSerializedAttributeValue(String name, T value) {
        setSerializedAttributeValues(name, value instanceof List ? (List<Object>) value : Arrays.asList(value));
    }

    private void setSerializedAttributeValues(String name, List<?> values) {
        Set<String> attributeValues = values.stream()
            .map(value -> {
                try {
                    return CassandraJsonSerialization.writeValueAsString(value);
                } catch (IOException e) {
                    log.errorf("Cannot serialize %s (realm: %s, name: %s)", value, realmEntity.getId(), name);
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toCollection(HashSet::new));

        realmEntity.getAttributes().put(name, attributeValues);
        realmRepository.insertOrUpdate(realmEntity);
    }

    private <T> T getDeserializedAttribute(String name, TypeReference<T> type) {
        return getDeserializedAttributes(name, type).stream().findFirst().orElse(null);
    }

    private <T> T getDeserializedAttribute(String name, Class<T> type) {
        return getDeserializedAttributes(name, type).stream().findFirst().orElse(null);
    }

    private <T> List<T> getDeserializedAttributes(String name, TypeReference<T> type) {
        Set<String> values = realmEntity.getAttribute(name);
        if (values == null) {
            return new ArrayList<>();
        }

        return values.stream()
            .map(value -> {
                try {
                    return CassandraJsonSerialization.readValue(value, type);
                } catch (IOException e) {
                    log.errorf("Cannot deserialize %s (realm: %s, name: %s)", value, realmEntity.getId(), name);
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private <T> List<T> getDeserializedAttributes(String name, Class<T> type) {
        Set<String> values = realmEntity.getAttribute(name);

        return values.stream()
            .map(value -> {
                try {
                    return CassandraJsonSerialization.readValue(value, type);
                } catch (IOException e) {
                    log.errorf("Cannot deserialize %s (realm: %s, name: %s, type: %s)", value, realmEntity.getId(), name, type.getName());
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

    // Clients
    @Override
    public Stream<ClientModel> getClientsStream() {
        return session.clients().getClientsStream(this);
    }

    @Override
    public Stream<ClientModel> getClientsStream(Integer firstResult, Integer maxResults) {
        return session.clients().getClientsStream(this, firstResult, maxResults);
    }

    @Override
    public Long getClientsCount() {
        return session.clients().getClientsCount(this);
    }

    @Override
    public Stream<ClientModel> getAlwaysDisplayInConsoleClientsStream() {
        return session.clients().getAlwaysDisplayInConsoleClientsStream(this);
    }

    @Override
    public ClientModel addClient(String name) {
        return session.clients().addClient(this, name);
    }

    @Override
    public ClientModel addClient(String id, String clientId) {
        return session.clients().addClient(this, id, clientId);
    }

    @Override
    public boolean removeClient(String id) {
        return session.clients().removeClient(this, id);
    }

    @Override
    public ClientModel getClientById(String id) {
        return session.clients().getClientById(this, id);
    }

    @Override
    public ClientModel getClientByClientId(String clientId) {
        return session.clients().getClientByClientId(this, clientId);
    }

    @Override
    public Stream<ClientModel> searchClientByClientIdStream(String clientId, Integer firstResult, Integer maxResults) {
        return session.clients().searchClientsByClientIdStream(this, clientId, firstResult, maxResults);
    }

    @Override
    public Stream<ClientModel> searchClientByAttributes(Map<String, String> attributes, Integer firstResult, Integer maxResults) {
        return session.clients().searchClientsByAttributes(this, attributes, firstResult, maxResults);
    }

    // Client Scopes
    @Override
    public Stream<ClientScopeModel> getClientScopesStream() {
        return session.clientScopes().getClientScopesStream(this);
    }

    @Override
    public ClientScopeModel addClientScope(String name) {
        return session.clientScopes().addClientScope(this, name);
    }

    @Override
    public ClientScopeModel addClientScope(String id, String name) {
        return session.clientScopes().addClientScope(this, id, name);
    }

    @Override
    public boolean removeClientScope(String id) {
        return session.clientScopes().removeClientScope(this, id);
    }

    @Override
    public ClientScopeModel getClientScopeById(String id) {
        return session.clientScopes().getClientScopeById(this, id);
    }

    @Override
    public void addDefaultClientScope(ClientScopeModel clientScope, boolean defaultScope) {
        String attrName = defaultScope ? DEFAULT_CLIENT_SCOPE_ID : OPTIONAL_CLIENT_SCOPE_ID;
        Set<String> values = realmEntity.getAttribute(attrName);
        values.add(clientScope.getId());
        realmEntity.getAttributes().put(attrName, values);
        realmRepository.insertOrUpdate(realmEntity);
    }

    @Override
    public void removeDefaultClientScope(ClientScopeModel clientScope) {
        if (realmEntity.getAttribute(DEFAULT_CLIENT_SCOPE_ID).contains(clientScope.getId())) {
            realmEntity.getAttribute(DEFAULT_CLIENT_SCOPE_ID).remove(clientScope.getId());
        } else {
            realmEntity.getAttribute(OPTIONAL_CLIENT_SCOPE_ID).remove(clientScope.getId());
        }

        realmRepository.insertOrUpdate(realmEntity);
    }

    @Override
    public Stream<ClientScopeModel> getDefaultClientScopesStream(boolean defaultScope) {
        Set<String> values = defaultScope
            ? realmEntity.getAttribute(DEFAULT_CLIENT_SCOPE_ID)
            : realmEntity.getAttribute(OPTIONAL_CLIENT_SCOPE_ID);
        return values.stream().map(this::getClientScopeById);
    }

    // Groups
    @Override
    public Stream<GroupModel> getDefaultGroupsStream() {
        Set<String> values = realmEntity.getAttribute(DEFAULT_GROUP_IDS);
        return values.stream().map(this::getGroupById);
    }

    @Override
    public void addDefaultGroup(GroupModel group) {
        Set<String> values = realmEntity.getAttribute(DEFAULT_GROUP_IDS);
        values.add(group.getId());
        realmRepository.insertOrUpdate(realmEntity);
    }

    @Override
    public void removeDefaultGroup(GroupModel group) {
        Set<String> values = realmEntity.getAttribute(DEFAULT_GROUP_IDS);
        values.remove(group.getId());
        realmRepository.insertOrUpdate(realmEntity);
    }

    @Override
    public GroupModel createGroup(String id, String name, GroupModel toParent) {
        return session.groups().createGroup(this, id, name, toParent);
    }

    @Override
    public GroupModel getGroupById(String id) {
        return session.groups().getGroupById(this, id);
    }

    @Override
    public Stream<GroupModel> getGroupsStream() {
        return session.groups().getGroupsStream(this);
    }

    @Override
    public Long getGroupsCount(Boolean onlyTopGroups) {
        return session.groups().getGroupsCount(this, onlyTopGroups);
    }

    @Override
    public Long getGroupsCountByNameContaining(String search) {
        return session.groups().getGroupsCountByNameContaining(this, search);
    }

    @Override
    public Stream<GroupModel> getTopLevelGroupsStream() {
        return session.groups().getTopLevelGroupsStream(this);
    }

    @Override
    public Stream<GroupModel> getTopLevelGroupsStream(Integer first, Integer max) {
        return session.groups().getTopLevelGroupsStream(this, first, max);
    }

    @Override
    @Deprecated
    public Stream<GroupModel> searchForGroupByNameStream(String search, Integer first, Integer max) {
        return session.groups().searchForGroupByNameStream(this, search, false, first, max);
    }

    @Override
    public boolean removeGroup(GroupModel group) {
        return session.groups().removeGroup(this, group);
    }

    @Override
    public void moveGroup(GroupModel group, GroupModel toParent) {
        session.groups().moveGroup(this, group, toParent);
    }

    // Roles
    @Override
    public RoleModel getDefaultRole() {
        String defaultRoleId = getAttribute(DEFAULT_ROLE_ID);
        if (defaultRoleId == null) {
            return null;
        }
        return getRoleById(defaultRoleId);
    }


    @Override
    public void setDefaultRole(RoleModel role) {
        realmEntity.getAttributes().put(DEFAULT_ROLE_ID, new HashSet<>(Arrays.asList(role.getId())));
        realmRepository.insertOrUpdate(realmEntity);
    }

    @Override
    public RoleModel getRole(String name) {
        return session.roles().getRealmRole(this, name);
    }

    @Override
    public RoleModel addRole(String name) {
        return session.roles().addRealmRole(this, name);
    }

    @Override
    public RoleModel addRole(String id, String name) {
        return session.roles().addRealmRole(this, id, name);
    }

    @Override
    public boolean removeRole(RoleModel role) {
        return session.roles().removeRole(role);
    }

    @Override
    public Stream<RoleModel> getRolesStream() {
        return session.roles().getRealmRolesStream(this);
    }

    @Override
    public Stream<RoleModel> getRolesStream(Integer firstResult, Integer maxResults) {
        return session.roles().getRealmRolesStream(this, firstResult, maxResults);
    }

    @Override
    public Stream<RoleModel> searchForRolesStream(String search, Integer first, Integer max) {
        return session.roles().searchForRolesStream(this, search, first, max);
    }

    @Override
    @Deprecated
    public Stream<String> getDefaultRolesStream() {
        return getDefaultRole().getCompositesStream().filter(this::isRealmRole).map(RoleModel::getName);
    }

    private boolean isRealmRole(RoleModel role) {
        return !role.isClientRole();
    }

    @Override
    @Deprecated
    public void addDefaultRole(String name) {
        getDefaultRole().addCompositeRole(getOrAddRoleId(name));
    }

    private RoleModel getOrAddRoleId(String name) {
        RoleModel role = getRole(name);
        if (role == null) {
            role = addRole(name);
        }
        return role;
    }

    @Override
    @Deprecated
    public void removeDefaultRoles(String... defaultRoles) {
        for (String defaultRole : defaultRoles) {
            getDefaultRole().removeCompositeRole(getRole(defaultRole));
        }
    }

    @Override
    public RoleModel getRoleById(String id) {
        return session.roles().getRoleById(this, id);
    }

    private ClientInitialAccessModel toModel(ClientInitialAccess entity) {
        ClientInitialAccessModel model = new ClientInitialAccessModel();
        model.setId(entity.getId());
        model.setCount(entity.getCount() == null ? 0 : entity.getCount());
        model.setRemainingCount(entity.getRemainingCount() == null ? 0 : entity.getRemainingCount());

        Long expirationSeconds = TimeAdapter.fromMilliSecondsToSeconds(entity.getExpiration());
        model.setExpiration(expirationSeconds == null ? 0 : TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(expirationSeconds - model.getTimestamp()));

        Long timestampSeconds = TimeAdapter.fromMilliSecondsToSeconds(entity.getTimestamp());
        model.setTimestamp(timestampSeconds == null ? 0 : TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(timestampSeconds));

        return model;
    }

}
