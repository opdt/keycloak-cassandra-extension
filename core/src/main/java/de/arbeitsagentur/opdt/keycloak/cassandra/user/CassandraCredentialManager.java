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
package de.arbeitsagentur.opdt.keycloak.cassandra.user;

import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.CredentialValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.reflections.Types;
import org.keycloak.credential.*;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@JBossLog
@RequiredArgsConstructor
public class CassandraCredentialManager implements SubjectCredentialManager {
    private static final int PRIORITY_DIFFERENCE = 10;
    private final KeycloakSession session;
    private final RealmModel realm;
    private final UserRepository userRepository;
    private final UserModel user;
    private final User userEntity;

    @Override
    public boolean isValid(List<CredentialInput> inputs) {
        if (!isValid(user)) {
            return false;
        }

        List<CredentialInput> toValidate = new LinkedList<>(inputs);

        getCredentialProviders(session, CredentialInputValidator.class)
            .forEach(validator -> validate(realm, user, toValidate, validator));

        return toValidate.isEmpty();
    }

    @Override
    public boolean updateCredential(CredentialInput input) {
        return getCredentialProviders(session, CredentialInputUpdater.class)
            .filter(updater -> updater.supportsCredentialType(input.getType()))
            .anyMatch(updater -> updater.updateCredential(realm, user, input));
    }

    @Override
    public void updateStoredCredential(CredentialModel cred) {
        throwExceptionIfInvalidUser(user);
        CredentialValue credential = fromModel(cred);
        userEntity.getCredentials().remove(credential);
        userEntity.getCredentials().add(credential);
        userRepository.createOrUpdateUser(realm.getId(), userEntity);
    }

    @Override
    public CredentialModel createStoredCredential(CredentialModel cred) {
        throwExceptionIfInvalidUser(user);
        boolean existsAlready = userEntity.hasCredential(cred.getId());

        if (existsAlready) {
            throw new ModelDuplicateException("A CredentialModel with given id already exists");
        }

        CredentialValue credential = fromModel(cred);

        List<CredentialValue> credentials = userEntity.getSortedCredentials();
        int priority = credentials.isEmpty() ? PRIORITY_DIFFERENCE : credentials.get(credentials.size() - 1).getPriority() + PRIORITY_DIFFERENCE;
        credential.setPriority(priority);

        userEntity.getCredentials().remove(credential);
        userEntity.getCredentials().add(credential);
        userRepository.createOrUpdateUser(realm.getId(), userEntity);

        return toModel(credential);
    }

    @Override
    public boolean removeStoredCredentialById(String id) {
        throwExceptionIfInvalidUser(user);

        boolean removed = userEntity.getCredentials().remove(CredentialValue.builder().id(id).build());

        if (!removed) {
            return false;
        }

        userRepository.createOrUpdateUser(realm.getId(), userEntity);
        return true;
    }

    @Override
    public CredentialModel getStoredCredentialById(String id) {
        CredentialValue credential = userEntity.getCredentials().stream()
            .filter(c -> c.getId().equals(id))
            .findFirst()
            .orElse(null);

        if (credential == null) {
            return null;
        }
        return toModel(credential);
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsStream() {
        return userEntity.getSortedCredentials().stream()
            .map(this::toModel);
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsByTypeStream(String type) {
        return getStoredCredentialsStream()
            .filter(credential -> Objects.equals(type, credential.getType()));
    }

    @Override
    public CredentialModel getStoredCredentialByNameAndType(String name, String type) {
        return getStoredCredentialsStream()
            .filter(credential -> Objects.equals(name, credential.getUserLabel()))
            .findFirst().orElse(null);
    }

    @Override
    public boolean moveStoredCredentialTo(String credentialId, String newPreviousCredentialId) {
        throwExceptionIfInvalidUser(user);

        // 1 - Get all credentials from the entity.
        List<CredentialValue> credentialsList = userEntity.getSortedCredentials();

        // 2 - Find indexes of our and newPrevious credential
        int ourCredentialIndex = -1;
        int newPreviousCredentialIndex = -1;
        CredentialValue ourCredential = null;
        int i = 0;
        for (CredentialValue credential : credentialsList) {
            if (credentialId.equals(credential.getId())) {
                ourCredentialIndex = i;
                ourCredential = credential;
            } else if (newPreviousCredentialId != null && newPreviousCredentialId.equals(credential.getId())) {
                newPreviousCredentialIndex = i;
            }
            i++;
        }

        if (ourCredentialIndex == -1) {
            log.debugf("Not found credential with id [%s] of user [%s]", credentialId, user.getUsername());
            return false;
        }

        if (newPreviousCredentialId != null && newPreviousCredentialIndex == -1) {
            log.debugf("Can't move up credential with id [%s] of user [%s]", credentialId, user.getUsername());
            return false;
        }

        // 3 - Compute index where we move our credential
        int toMoveIndex = newPreviousCredentialId == null ? 0 : newPreviousCredentialIndex + 1;

        // 4 - Insert our credential to new position, remove it from the old position
        if (toMoveIndex == ourCredentialIndex) return true;
        credentialsList.add(toMoveIndex, ourCredential);
        int indexToRemove = toMoveIndex < ourCredentialIndex ? ourCredentialIndex + 1 : ourCredentialIndex;
        credentialsList.remove(indexToRemove);

        // 5 - newList contains credentials in requested order now. Iterate through whole list and change priorities accordingly.
        int expectedPriority = 0;
        for (CredentialValue credential : credentialsList) {
            expectedPriority += PRIORITY_DIFFERENCE;
            if (credential.getPriority() != expectedPriority) {
                credential.setPriority(expectedPriority);

                log.tracef("Priority of credential [%s] of user [%s] changed to [%d]", credential.getId(), user.getUsername(), expectedPriority);

                userEntity.getCredentials().remove(credential);
                userEntity.getCredentials().add(credential);
                userRepository.createOrUpdateUser(realm.getId(), userEntity);
            }
        }

        return true;
    }

    @Override
    public void updateCredentialLabel(String credentialId, String userLabel) {
        throwExceptionIfInvalidUser(user);
        CredentialModel credential = getStoredCredentialById(credentialId);
        credential.setUserLabel(userLabel);
        updateStoredCredential(credential);
    }

    @Override
    public void disableCredentialType(String credentialType) {
        getCredentialProviders(session, CredentialInputUpdater.class)
            .filter(updater -> updater.supportsCredentialType(credentialType))
            .forEach(updater -> updater.disableCredentialType(realm, user, credentialType));
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream() {
        return getCredentialProviders(session, CredentialInputUpdater.class)
            .flatMap(updater -> updater.getDisableableCredentialTypesStream(realm, user))
            .distinct();
    }

    @Override
    public boolean isConfiguredFor(String type) {
        return getCredentialProviders(session, CredentialInputValidator.class)
            .anyMatch(validator -> validator.supportsCredentialType(type) && validator.isConfiguredFor(realm, user, type));
    }

    @Override
    @Deprecated
    public boolean isConfiguredLocally(String type) {
        throw new IllegalArgumentException("this is not supported for map storage");
    }

    @Override
    @Deprecated
    public Stream<String> getConfiguredUserStorageCredentialTypesStream() {
        // used in the old admin console for users to determine if a password is set for a user
        // not used in the new admin console
        return Stream.empty();
    }

    @Override
    @Deprecated
    public CredentialModel createCredentialThroughProvider(CredentialModel model) {
        // this is still called when importing/creating a user via RepresentationToModel.createCredentials
        throwExceptionIfInvalidUser(user);
        return session.getKeycloakSessionFactory()
            .getProviderFactoriesStream(CredentialProvider.class)
            .map(f -> session.getProvider(CredentialProvider.class, f.getId()))
            .filter(provider -> Objects.equals(provider.getType(), model.getType()))
            .map(cp -> cp.createCredential(realm, user, cp.getCredentialFromModel(model)))
            .findFirst()
            .orElse(null);
    }

    private boolean isValid(UserModel user) {
        Objects.requireNonNull(user);
        return user.getServiceAccountClientLink() == null;
    }

    private void validate(RealmModel realm, UserModel user, List<CredentialInput> toValidate, CredentialInputValidator validator) {
        toValidate.removeIf(input -> validator.supportsCredentialType(input.getType()) && validator.isValid(realm, user, input));
    }

    private static <T> Stream<T> getCredentialProviders(KeycloakSession session, Class<T> type) {
        //noinspection unchecked
        return session.getKeycloakSessionFactory().getProviderFactoriesStream(CredentialProvider.class)
            .filter(f -> Types.supports(type, f, CredentialProviderFactory.class))
            .map(f -> (T) session.getProvider(CredentialProvider.class, f.getId()));
    }

    private void throwExceptionIfInvalidUser(UserModel user) {
        if (!isValid(user)) {
            throw new RuntimeException("You can not manage credentials for this user");
        }
    }

    private CredentialValue fromModel(CredentialModel model) {
        return CredentialValue.builder()
            .id(model.getId() == null ? KeycloakModelUtils.generateId() : model.getId())
            .created(model.getCreatedDate())
            .userLabel(model.getUserLabel())
            .type(model.getType())
            .secretData(model.getSecretData())
            .credentialData(model.getCredentialData())
            .build();
    }

    private CredentialModel toModel(CredentialValue entity) {
        CredentialModel credentialModel = new CredentialModel();
        credentialModel.setId(entity.getId());
        credentialModel.setCreatedDate(entity.getCreated());
        credentialModel.setUserLabel(entity.getUserLabel());
        credentialModel.setType(entity.getType());
        credentialModel.setSecretData(entity.getSecretData());
        credentialModel.setCredentialData(entity.getCredentialData());

        return credentialModel;
    }
}
