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
package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import de.arbeitsagentur.opdt.keycloak.cassandra.realm.CassandraRealmAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Test;
import org.keycloak.authorization.AuthorizationProvider;
import org.keycloak.authorization.model.ResourceServer;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.common.util.Time;
import org.keycloak.component.ComponentModel;
import org.keycloak.keys.KeyProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.ProviderEventListener;

public class RealmModelTest extends KeycloakModelTest {

  private String realmId;
  private String realm1Id;
  private String realm2Id;

  @Override
  public void createEnvironment(KeycloakSession s) {
    RealmModel realm = s.realms().createRealm("master");
    realm.setDefaultRole(
        s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
    this.realmId = realm.getId();
  }

  @Override
  public void cleanEnvironment(KeycloakSession s) {
    s.realms().removeRealm(realmId);
    if (realm1Id != null) s.realms().removeRealm(realm1Id);
    if (realm2Id != null) s.realms().removeRealm(realm2Id);
  }

  @Test
  public void staleRealmUpdate() {
    withRealm(
        realmId,
        (session, realm) -> {
          realm.setAttribute("key", "val");

          return null;
        });

    boolean staleExceptionOccured = false;
    try {
      withRealm(
          realmId,
          (session, realm) -> {
            assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("3"));

            realm.setAttribute(CassandraRealmAdapter.ENTITY_VERSION, "2");

            return null;
          });
    } catch (Exception e) {
      staleExceptionOccured = true;
    }

    assertTrue(staleExceptionOccured);

    staleExceptionOccured = false;
    try {
      withRealm(
          realmId,
          (session, realm) -> {
            assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("3"));

            realm.setAttribute(CassandraRealmAdapter.ENTITY_VERSION, "4");

            return null;
          });
    } catch (Exception e) {
      staleExceptionOccured = true;
    }

    assertTrue(staleExceptionOccured);
  }

  @Test
  public void workingRealmUpdate() {
    withRealm(
        realmId,
        (session, realm) -> {
          realm.setAttribute("key", "val");

          return null;
        });

    withRealm(
        realmId,
        (session, realm) -> {
          assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("3"));

          realm.setAttribute("key", "val2");

          return null;
        });

    withRealm(
        realmId,
        (session, realm) -> {
          assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("4"));
          assertThat(realm.getAttribute("key"), is("val2"));

          return null;
        });
  }

  @Test
  public void getRealmByName() {
    inComittedTransaction(
        s -> {
          s.realms().createRealm("my-realm");
          RealmModel realmByName = s.realms().getRealmByName("my-realm");

          assertThat(realmByName.getName(), is("my-realm"));

          realmByName.setName("my-updated-realm");
        });

    inComittedTransaction(
        s -> {
          RealmModel realmByName = s.realms().getRealmByName("my-updated-realm");

          assertThat(realmByName.getName(), is("my-updated-realm"));

          s.realms().removeRealm(realmByName.getId());
          assertNull(s.realms().getRealmByName("my-updated-realm"));
        });
  }

  @Test
  public void entityVersionAttribute() {
    withRealm(
        realmId,
        (session, realm) -> {
          assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("2"));
          assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION_READONLY), is("2"));

          realm.setAttribute(CassandraRealmAdapter.ENTITY_VERSION_READONLY, "42");
          assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("2"));
          assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION_READONLY), is("2"));

          realm.setAttribute(CassandraRealmAdapter.ENTITY_VERSION, "42");
          assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("42"));
          assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION_READONLY), is("42"));

          realm.setAttribute(CassandraRealmAdapter.ENTITY_VERSION, "2");
          return null;
        });
  }

  @Test
  public void testRealmLocalizationTexts() {
    withRealm(
        realmId,
        (session, realm) -> {
          // Assert emptyMap
          assertThat(realm.getRealmLocalizationTexts(), anEmptyMap());
          // Add a localization test
          session.realms().saveLocalizationTexts(realm, "en", Map.of("key-a", "text-a_en"));
          return null;
        });

    withRealm(
        realmId,
        (session, realm) -> {
          // Assert the map contains the added value
          assertThat(realm.getRealmLocalizationTexts(), aMapWithSize(1));
          assertThat(
              realm.getRealmLocalizationTexts(),
              hasEntry(
                  equalTo("en"),
                  allOf(aMapWithSize(1), hasEntry(equalTo("key-a"), equalTo("text-a_en")))));
          assertThat(
              session.realms().getLocalizationTextsById(realm, "en", "key-a"), is("text-a_en"));

          // Add another localization text to previous locale
          session
              .realms()
              .saveLocalizationTexts(
                  realm, "en", Map.of("key-a", "text-a_en", "key-b", "text-b_en"));
          session.realms().saveLocalizationText(realm, "en", "key-c", "text-c_en");
          return null;
        });

    withRealm(
        realmId,
        (session, realm) -> {
          assertThat(realm.getRealmLocalizationTexts(), aMapWithSize(1));
          assertThat(
              realm.getRealmLocalizationTexts(),
              hasEntry(
                  equalTo("en"),
                  allOf(
                      aMapWithSize(3),
                      hasEntry(equalTo("key-a"), equalTo("text-a_en")),
                      hasEntry(equalTo("key-b"), equalTo("text-b_en")),
                      hasEntry(equalTo("key-c"), equalTo("text-c_en")))));

          // Add new locale
          session.realms().saveLocalizationText(realm, "de", "key-a", "text-a_de");
          return null;
        });

    withRealm(
        realmId,
        (session, realm) -> {
          // Check everything created successfully
          assertThat(realm.getRealmLocalizationTexts(), aMapWithSize(2));
          assertThat(
              realm.getRealmLocalizationTexts(),
              hasEntry(
                  equalTo("en"),
                  allOf(
                      aMapWithSize(3),
                      hasEntry(equalTo("key-a"), equalTo("text-a_en")),
                      hasEntry(equalTo("key-b"), equalTo("text-b_en")),
                      hasEntry(equalTo("key-c"), equalTo("text-c_en")))));
          assertThat(
              realm.getRealmLocalizationTexts(),
              hasEntry(
                  equalTo("de"),
                  allOf(aMapWithSize(1), hasEntry(equalTo("key-a"), equalTo("text-a_de")))));

          return null;
        });

    withRealm(
        realmId,
        (session, realm) -> {
          session.realms().updateLocalizationText(realm, "en", "key-b", "updated");
          assertThat(
              session.realms().getLocalizationTextsById(realm, "en", "key-b"), is("updated"));

          session.realms().deleteLocalizationText(realm, "en", "key-a");
          return null;
        });

    withRealm(
        realmId,
        (session, realm) -> {
          assertThat(realm.getRealmLocalizationTexts(), aMapWithSize(2));
          assertThat(
              realm.getRealmLocalizationTexts(),
              hasEntry(
                  equalTo("en"),
                  allOf(
                      aMapWithSize(2),
                      hasEntry(equalTo("key-b"), equalTo("updated")),
                      hasEntry(equalTo("key-c"), equalTo("text-c_en")))));
          assertThat(
              realm.getRealmLocalizationTexts(),
              hasEntry(
                  equalTo("de"),
                  allOf(aMapWithSize(1), hasEntry(equalTo("key-a"), equalTo("text-a_de")))));

          assertThat(
              session.realms().getLocalizationTextsById(realm, "en", "key-b"), is("updated"));

          session.realms().deleteLocalizationTextsByLocale(realm, "de");
          return null;
        });

    withRealm(
        realmId,
        (session, realm) -> {
          assertThat(realm.getRealmLocalizationTexts(), aMapWithSize(1));
          assertThat(
              realm.getRealmLocalizationTexts(),
              hasEntry(
                  equalTo("en"),
                  allOf(
                      aMapWithSize(2),
                      hasEntry(equalTo("key-b"), equalTo("updated")),
                      hasEntry(equalTo("key-c"), equalTo("text-c_en")))));

          return null;
        });
  }

  @Test
  @Ignore("Authorization is not supported currently")
  public void testRealmPreRemoveDoesntRemoveEntitiesFromOtherRealms() {
    realm1Id =
        inComittedTransaction(
            session -> {
              RealmModel realm = session.realms().createRealm("realm1");
              realm.setDefaultRole(
                  session
                      .roles()
                      .addRealmRole(
                          realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
              return realm.getId();
            });
    realm2Id =
        inComittedTransaction(
            session -> {
              RealmModel realm = session.realms().createRealm("realm2");
              realm.setDefaultRole(
                  session
                      .roles()
                      .addRealmRole(
                          realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
              return realm.getId();
            });

    // Create client with resource server
    String clientRealm1 =
        withRealm(
            realm1Id,
            (keycloakSession, realmModel) -> {
              ClientModel clientRealm = realmModel.addClient("clientRealm1");
              AuthorizationProvider provider =
                  keycloakSession.getProvider(AuthorizationProvider.class);
              provider.getStoreFactory().getResourceServerStore().create(clientRealm);

              return clientRealm.getId();
            });

    // Remove realm 2
    inComittedTransaction(
        (Consumer<KeycloakSession>)
            keycloakSession -> keycloakSession.realms().removeRealm(realm2Id));

    // ResourceServer in realm1 must still exist
    ResourceServer resourceServer =
        withRealm(
            realm1Id,
            (keycloakSession, realmModel) -> {
              ClientModel client1 = realmModel.getClientById(clientRealm1);
              return keycloakSession
                  .getProvider(AuthorizationProvider.class)
                  .getStoreFactory()
                  .getResourceServerStore()
                  .findByClient(client1);
            });

    assertThat(resourceServer, notNullValue());
  }

  @Test
  public void testMoveGroup() {
    ProviderEventListener providerEventListener = null;
    try {
      List<GroupModel.GroupPathChangeEvent> groupPathChangeEvents = new ArrayList<>();
      providerEventListener =
          event -> {
            if (event instanceof GroupModel.GroupPathChangeEvent) {
              groupPathChangeEvents.add((GroupModel.GroupPathChangeEvent) event);
            }
          };
      getFactory().register(providerEventListener);

      withRealm(
          realmId,
          (session, realm) -> {
            GroupModel groupA = realm.createGroup("a");
            GroupModel groupB = realm.createGroup("b");

            final String previousPath = "/a";
            assertThat(KeycloakModelUtils.buildGroupPath(groupA), equalTo(previousPath));

            realm.moveGroup(groupA, groupB);

            final String expectedNewPath = "/b/a";
            assertThat(KeycloakModelUtils.buildGroupPath(groupA), equalTo(expectedNewPath));

            assertThat(groupPathChangeEvents, hasSize(1));
            GroupModel.GroupPathChangeEvent groupPathChangeEvent = groupPathChangeEvents.get(0);
            assertThat(groupPathChangeEvent.getPreviousPath(), equalTo(previousPath));
            assertThat(groupPathChangeEvent.getNewPath(), equalTo(expectedNewPath));

            return null;
          });
    } finally {
      if (providerEventListener != null) {
        getFactory().unregister(providerEventListener);
      }
    }
  }

  @Test
  public void testAuthenticationFlows() {
    String flowId =
        withRealm(
            realmId,
            (s, realm) -> {
              AuthenticationFlowModel browser = new AuthenticationFlowModel();
              browser.setAlias("myFlow");
              browser.setDescription("browser based authentication");
              browser.setProviderId("basic-flow");
              browser.setTopLevel(true);
              browser.setBuiltIn(true);

              return realm.addAuthenticationFlow(browser).getId();
            });

    withRealm(
        realmId,
        (s, realm) -> {
          AuthenticationFlowModel readFlow = realm.getAuthenticationFlowById(flowId);
          assertThat(readFlow.getAlias(), is("myFlow"));
          assertThat(readFlow.getDescription(), is("browser based authentication"));
          assertThat(readFlow.getProviderId(), is("basic-flow"));
          assertTrue(readFlow.isTopLevel());
          assertTrue(readFlow.isBuiltIn());

          readFlow.setDescription("test");

          realm.updateAuthenticationFlow(readFlow);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          AuthenticationFlowModel readFlow = realm.getAuthenticationFlowById(flowId);
          assertThat(readFlow.getAlias(), is("myFlow"));
          assertThat(readFlow.getDescription(), is("test"));
          assertThat(readFlow.getProviderId(), is("basic-flow"));
          assertTrue(readFlow.isTopLevel());
          assertTrue(readFlow.isBuiltIn());

          realm.removeAuthenticationFlow(readFlow);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          AuthenticationFlowModel readFlow = realm.getAuthenticationFlowById(flowId);
          assertNull(readFlow);

          return null;
        });
  }

  @Test
  public void testAuthenticatorExecutions() {
    String executionId =
        withRealm(
            realmId,
            (s, realm) -> {
              AuthenticationExecutionModel execution = new AuthenticationExecutionModel();
              execution.setParentFlow("test");
              execution.setRequirement(AuthenticationExecutionModel.Requirement.REQUIRED);
              execution.setAuthenticator("username-only");
              execution.setPriority(10);
              execution.setAuthenticatorFlow(false);

              return realm.addAuthenticatorExecution(execution).getId();
            });

    withRealm(
        realmId,
        (s, realm) -> {
          AuthenticationExecutionModel execution =
              realm.getAuthenticationExecutionById(executionId);
          assertThat(execution.getParentFlow(), is("test"));
          assertThat(
              execution.getRequirement(), is(AuthenticationExecutionModel.Requirement.REQUIRED));
          assertThat(execution.getAuthenticator(), is("username-only"));
          assertThat(execution.getPriority(), is(10));
          assertFalse(execution.isAuthenticatorFlow());

          execution.setRequirement(AuthenticationExecutionModel.Requirement.ALTERNATIVE);

          realm.updateAuthenticatorExecution(execution);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          AuthenticationExecutionModel execution =
              realm.getAuthenticationExecutionById(executionId);
          assertThat(execution.getParentFlow(), is("test"));
          assertThat(
              execution.getRequirement(), is(AuthenticationExecutionModel.Requirement.ALTERNATIVE));
          assertThat(execution.getAuthenticator(), is("username-only"));
          assertThat(execution.getPriority(), is(10));
          assertFalse(execution.isAuthenticatorFlow());

          realm.removeAuthenticatorExecution(execution);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          AuthenticationExecutionModel execution =
              realm.getAuthenticationExecutionById(executionId);
          assertNull(execution);

          return null;
        });
  }

  @Test
  public void testAuthenticatorConfigs() {
    String configId =
        withRealm(
            realmId,
            (s, realm) -> {
              AuthenticatorConfigModel config = new AuthenticatorConfigModel();
              config.setAlias("test");
              config.setConfig(Map.of("key1", "val1", "key2", "val2"));

              return realm.addAuthenticatorConfig(config).getId();
            });

    withRealm(
        realmId,
        (s, realm) -> {
          AuthenticatorConfigModel config = realm.getAuthenticatorConfigById(configId);
          assertThat(config.getAlias(), is("test"));
          assertThat(config.getConfig().entrySet(), hasSize(2));
          assertThat(config.getConfig().get("key1"), is("val1"));
          assertThat(config.getConfig().get("key2"), is("val2"));

          config.getConfig().put("key1", "updatedVal1");

          realm.updateAuthenticatorConfig(config);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          AuthenticatorConfigModel config = realm.getAuthenticatorConfigByAlias("test");
          assertThat(config.getAlias(), is("test"));
          assertThat(config.getConfig().entrySet(), hasSize(2));
          assertThat(config.getConfig().get("key1"), is("updatedVal1"));
          assertThat(config.getConfig().get("key2"), is("val2"));

          realm.removeAuthenticatorConfig(config);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          AuthenticatorConfigModel config = realm.getAuthenticatorConfigByAlias("test");
          assertNull(config);

          return null;
        });
  }

  @Test
  public void testRequiredActionProviders() {
    String providerId =
        withRealm(
            realmId,
            (s, realm) -> {
              RequiredActionProviderModel requiredActionProviderModel =
                  new RequiredActionProviderModel();
              requiredActionProviderModel.setAlias("test");
              requiredActionProviderModel.setProviderId("consent-provider");
              requiredActionProviderModel.setDefaultAction(false);
              requiredActionProviderModel.setPriority(20);
              requiredActionProviderModel.setEnabled(true);

              return realm.addRequiredActionProvider(requiredActionProviderModel).getId();
            });

    withRealm(
        realmId,
        (s, realm) -> {
          RequiredActionProviderModel provider = realm.getRequiredActionProviderById(providerId);
          assertThat(provider.getAlias(), is("test"));
          assertThat(provider.getProviderId(), is("consent-provider"));
          assertFalse(provider.isDefaultAction());
          assertThat(provider.getPriority(), is(20));
          assertTrue(provider.isEnabled());

          provider.setProviderId("test-provider");

          realm.updateRequiredActionProvider(provider);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          RequiredActionProviderModel provider = realm.getRequiredActionProviderByAlias("test");
          assertThat(provider.getAlias(), is("test"));
          assertThat(provider.getProviderId(), is("test-provider"));
          assertFalse(provider.isDefaultAction());
          assertThat(provider.getPriority(), is(20));
          assertTrue(provider.isEnabled());

          realm.removeRequiredActionProvider(provider);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          RequiredActionProviderModel provider = realm.getRequiredActionProviderByAlias("test");
          assertNull(provider);

          return null;
        });
  }

  @Test
  public void testIdentityProviders() {
    withRealm(
        realmId,
        (s, realm) -> {
          IdentityProviderModel provider = new IdentityProviderModel();
          provider.setAlias("test");
          provider.setProviderId("idp-provider");
          provider.setDisplayName("External IDP");
          provider.setEnabled(true);

          realm.addIdentityProvider(provider);
          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          IdentityProviderModel provider = realm.getIdentityProviderByAlias("test");
          assertThat(provider.getAlias(), is("test"));
          assertThat(provider.getProviderId(), is("idp-provider"));
          assertThat(provider.getDisplayName(), is("External IDP"));
          assertTrue(provider.isEnabled());

          assertTrue(realm.isIdentityFederationEnabled());

          provider.setProviderId("test-provider");

          realm.updateIdentityProvider(provider);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          IdentityProviderModel provider = realm.getIdentityProviderByAlias("test");
          assertThat(provider.getAlias(), is("test"));
          assertThat(provider.getProviderId(), is("test-provider"));
          assertThat(provider.getDisplayName(), is("External IDP"));
          assertTrue(provider.isEnabled());

          realm.removeIdentityProviderByAlias("test");

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          IdentityProviderModel provider = realm.getIdentityProviderByAlias("test");
          assertNull(provider);
          assertFalse(realm.isIdentityFederationEnabled());

          return null;
        });
  }

  @Test
  public void testIdentityProviderMappers() {
    String mapperId =
        withRealm(
            realmId,
            (s, realm) -> {
              IdentityProviderMapperModel mapper = new IdentityProviderMapperModel();
              mapper.setName("test");
              mapper.setIdentityProviderMapper("username");
              mapper.setIdentityProviderAlias("testIdp");
              mapper.setConfig(Map.of("key1", "value1"));

              return realm.addIdentityProviderMapper(mapper).getId();
            });

    withRealm(
        realmId,
        (s, realm) -> {
          IdentityProviderMapperModel mapper = realm.getIdentityProviderMapperById(mapperId);
          assertThat(mapper.getName(), is("test"));
          assertThat(mapper.getIdentityProviderMapper(), is("username"));
          assertThat(mapper.getIdentityProviderAlias(), is("testIdp"));
          assertThat(mapper.getConfig().entrySet(), hasSize(1));
          assertThat(mapper.getConfig().get("key1"), is("value1"));

          mapper.getConfig().put("key2", "value2");

          realm.updateIdentityProviderMapper(mapper);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          IdentityProviderMapperModel mapper =
              realm.getIdentityProviderMapperByName("testIdp", "test");
          assertThat(mapper.getName(), is("test"));
          assertThat(mapper.getIdentityProviderMapper(), is("username"));
          assertThat(mapper.getIdentityProviderAlias(), is("testIdp"));
          assertThat(mapper.getConfig().entrySet(), hasSize(2));
          assertThat(mapper.getConfig().get("key1"), is("value1"));
          assertThat(mapper.getConfig().get("key2"), is("value2"));

          realm.removeIdentityProviderMapper(mapper);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(
              realm.getIdentityProviderMappersByAliasStream("testIdp").collect(Collectors.toList()),
              hasSize(0));
          assertThat(
              realm.getIdentityProviderMappersStream().collect(Collectors.toList()), hasSize(0));

          return null;
        });
  }

  @Test
  public void testClientInitialAccesses() {
    String modelId =
        withRealm(realmId, (s, realm) -> realm.createClientInitialAccessModel(60, 2).getId());

    withRealm(
        realmId,
        (s, realm) -> {
          ClientInitialAccessModel clientInitialAccessModel =
              realm.getClientInitialAccessModel(modelId);
          assertThat(clientInitialAccessModel.getCount(), is(2));
          assertThat(clientInitialAccessModel.getRemainingCount(), is(2));

          realm.decreaseRemainingCount(clientInitialAccessModel);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          List<ClientInitialAccessModel> clientInitialAccessModels =
              realm.getClientInitialAccesses().collect(Collectors.toList());
          assertThat(clientInitialAccessModels, hasSize(1));
          assertThat(clientInitialAccessModels.get(0).getCount(), is(2));
          assertThat(clientInitialAccessModels.get(0).getRemainingCount(), is(1));

          realm.removeClientInitialAccessModel(modelId);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(realm.getClientInitialAccesses().collect(Collectors.toList()), hasSize(0));

          realm.createClientInitialAccessModel(200, 2);

          Time.setOffset(201);
          s.realms().removeExpiredClientInitialAccess();

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(realm.getClientInitialAccesses().collect(Collectors.toList()), hasSize(0));

          realm.createClientInitialAccessModel(3, 2);

          try {
            Thread.sleep(5000);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(realm.getClientInitialAccesses().collect(Collectors.toList()), hasSize(0));

          return null;
        });
  }

  @Test
  public void testComponents() {
    String componentId =
        withRealm(
            realmId,
            (s, realm) -> {
              ComponentModel component = new ComponentModel();
              component.setName("test");
              component.setParentId("testParent");
              component.setProviderType(KeyProvider.class.getName());
              component.setProviderId("aes-generated");
              component.setConfig(
                  new MultivaluedHashMap<>(Map.of("key1", List.of("value1", "value2"))));

              return realm.addComponentModel(component).getId();
            });

    withRealm(
        realmId,
        (s, realm) -> {
          ComponentModel model = realm.getComponent(componentId);
          assertThat(model.getName(), is("test"));
          assertThat(model.getParentId(), is("testParent"));
          assertThat(model.getProviderType(), is(KeyProvider.class.getName()));
          assertThat(model.getProviderId(), is("aes-generated"));
          assertThat(model.getConfig().get("key1"), is(List.of("value1", "value2")));

          List<RealmModel> realms =
              s.realms()
                  .getRealmsWithProviderTypeStream(KeyProvider.class)
                  .collect(Collectors.toList());
          assertThat(realms, hasSize(1));
          assertThat(realms.get(0), is(realm));

          model.getConfig().put("key1", List.of("value1", "value3"));

          realm.updateComponent(model);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          ComponentModel model = realm.getComponentsStream("testParent").findFirst().orElse(null);
          assertThat(model.getName(), is("test"));
          assertThat(model.getParentId(), is("testParent"));
          assertThat(model.getProviderType(), is(KeyProvider.class.getName()));
          assertThat(model.getProviderId(), is("aes-generated"));
          assertThat(model.getConfig().get("key1"), is(List.of("value1", "value3")));

          realm.removeComponent(model);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(realm.getComponentsStream().collect(Collectors.toList()), hasSize(0));
          assertThat(
              realm.getComponentsStream("testParent").collect(Collectors.toList()), hasSize(0));
          assertThat(
              realm
                  .getComponentsStream("testParent", KeyProvider.class.getName())
                  .collect(Collectors.toList()),
              hasSize(0));

          ComponentModel component = new ComponentModel();
          component.setName("test");
          component.setParentId("testParent");
          component.setProviderType(KeyProvider.class.getName());
          component.setProviderId("aes-generated");
          component.setConfig(
              new MultivaluedHashMap<>(Map.of("key1", List.of("value1", "value2"))));

          realm.addComponentModel(component);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          realm.removeComponents("testParent");

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(realm.getComponentsStream().collect(Collectors.toList()), hasSize(0));
          assertThat(
              realm.getComponentsStream("testParent").collect(Collectors.toList()), hasSize(0));
          assertThat(
              realm
                  .getComponentsStream("testParent", KeyProvider.class.getName())
                  .collect(Collectors.toList()),
              hasSize(0));

          return null;
        });
  }

  @Test
  public void testActionTokens() {
    withRealm(
        realmId,
        (s, realm) -> {
          realm.setActionTokenGeneratedByUserLifespan(42);
          realm.setActionTokenGeneratedByAdminLifespan(43);
          realm.setActionTokenGeneratedByUserLifespan("myTokenType", 100);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(realm.getActionTokenGeneratedByUserLifespan(), is(42));
          assertThat(realm.getActionTokenGeneratedByAdminLifespan(), is(43));
          assertThat(realm.getUserActionTokenLifespans().get("myTokenType"), is(100));
          assertThat(realm.getActionTokenGeneratedByUserLifespan("myTokenType"), is(100));

          return null;
        });
  }

  @Test
  public void testRequiredCredentials() {
    withRealm(
        realmId,
        (s, realm) -> {
          assertThrows(RuntimeException.class, () -> realm.addRequiredCredential("unknown"));
          realm.addRequiredCredential(RequiredCredentialModel.KERBEROS.getType());

          assertThrows(
              ModelDuplicateException.class,
              () -> realm.addRequiredCredential(RequiredCredentialModel.KERBEROS.getType()));

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(realm.getRequiredCredentialsStream().collect(Collectors.toList()), hasSize(1));

          RequiredCredentialModel requiredCredentialModel =
              realm.getRequiredCredentialsStream().collect(Collectors.toList()).get(0);
          assertThat(
              requiredCredentialModel.getType(), is(RequiredCredentialModel.KERBEROS.getType()));
          assertThat(
              requiredCredentialModel.getFormLabel(),
              is(RequiredCredentialModel.KERBEROS.getFormLabel()));
          assertThat(
              requiredCredentialModel.isInput(), is(RequiredCredentialModel.KERBEROS.isInput()));
          assertThat(
              requiredCredentialModel.isSecret(), is(RequiredCredentialModel.KERBEROS.isSecret()));

          RequiredCredentialModel.KERBEROS.setInput(true);
          RequiredCredentialModel.KERBEROS.setSecret(true);
          RequiredCredentialModel.KERBEROS.setFormLabel("changed");
          realm.updateRequiredCredentials(Set.of(RequiredCredentialModel.KERBEROS.getType()));

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(realm.getRequiredCredentialsStream().collect(Collectors.toList()), hasSize(1));

          RequiredCredentialModel requiredCredentialModel =
              realm.getRequiredCredentialsStream().collect(Collectors.toList()).get(0);
          assertThat(
              requiredCredentialModel.getType(), is(RequiredCredentialModel.KERBEROS.getType()));
          assertThat(requiredCredentialModel.getFormLabel(), is("changed"));
          assertThat(requiredCredentialModel.isInput(), is(true));
          assertThat(requiredCredentialModel.isSecret(), is(true));

          return null;
        });
  }

  @Test
  public void testMasterAdminClient() {
    withRealm(
        realmId,
        (s, realm) -> {
          ClientModel client = s.clients().addClient(realm, "adminClient");
          realm.setMasterAdminClient(client);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          ClientModel masterAdminClient = realm.getMasterAdminClient();
          assertThat(masterAdminClient.getClientId(), is("adminClient"));

          return null;
        });
  }

  @Test
  public void testDefaultGroup() {
    withRealm(
        realmId,
        (s, realm) -> {
          GroupModel group = s.groups().createGroup(realm, "myGroup");
          realm.addDefaultGroup(group);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(realm.getDefaultGroupsStream().collect(Collectors.toList()), hasSize(1));
          assertThat(
              realm.getDefaultGroupsStream().map(GroupModel::getName).findFirst().orElse(null),
              is("myGroup"));

          realm.removeDefaultGroup(
              realm.getDefaultGroupsStream().collect(Collectors.toList()).get(0));
          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(realm.getDefaultGroupsStream().collect(Collectors.toList()), hasSize(0));
          return null;
        });
  }

  @Test
  public void testDefaultClientScope() {
    withRealm(
        realmId,
        (s, realm) -> {
          ClientScopeModel clientScope = s.clientScopes().addClientScope(realm, "myClientScope");
          realm.addDefaultClientScope(clientScope, true);

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(
              realm.getDefaultClientScopesStream(true).collect(Collectors.toList()), hasSize(1));
          assertThat(
              realm.getDefaultClientScopesStream(false).collect(Collectors.toList()), hasSize(0));
          assertThat(
              realm
                  .getDefaultClientScopesStream(true)
                  .map(ClientScopeModel::getName)
                  .findFirst()
                  .orElse(null),
              is("myClientScope"));

          realm.removeDefaultClientScope(
              realm.getDefaultClientScopesStream(true).collect(Collectors.toList()).get(0));
          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(
              realm.getDefaultClientScopesStream(true).collect(Collectors.toList()), hasSize(0));
          assertThat(
              realm.getDefaultClientScopesStream(false).collect(Collectors.toList()), hasSize(0));

          return null;
        });
  }

  @Test
  public void testProperties() {
    withRealm(
        realmId,
        (s, realm) -> {
          realm.setPasswordPolicy(PasswordPolicy.parse(s, PasswordPolicy.PASSWORD_HISTORY_ID));
          realm.setAccountTheme("myTheme");
          realm.setAdminTheme("myTheme");
          realm.setEmailTheme("myTheme");
          realm.setEventsExpiration(42L);
          realm.setDefaultLocale("en-US");

          return null;
        });

    withRealm(
        realmId,
        (s, realm) -> {
          assertThat(
              realm.getPasswordPolicy().toString(),
              is(PasswordPolicy.parse(s, PasswordPolicy.PASSWORD_HISTORY_ID).toString()));
          assertThat(realm.getAccountTheme(), is("myTheme"));
          assertThat(realm.getAdminTheme(), is("myTheme"));
          assertThat(realm.getEmailTheme(), is("myTheme"));
          assertThat(realm.getEventsExpiration(), is(42L));
          assertThat(realm.getDefaultLocale(), is("en-US"));

          return null;
        });
  }
}
