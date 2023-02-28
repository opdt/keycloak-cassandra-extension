package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import org.junit.Test;
import org.keycloak.common.crypto.CryptoIntegration;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.*;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.services.resources.KeycloakApplication;
import org.keycloak.storage.DatastoreProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@RequireProvider(RealmProvider.class)
@RequireProvider(UserProvider.class)
@RequireProvider(RoleProvider.class)
@RequireProvider(DatastoreProvider.class)
public class CredentialModelTest extends KeycloakModelTest {

    private String realmId;

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().createRealm("test");
        realm.setDefaultRole(s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
        CryptoIntegration.init(KeycloakApplication.class.getClassLoader());

        UserModel user = s.users().addUser(realm, "test-user@localhost");
        user.credentialManager().updateCredential(UserCredentialModel.password("password"));

        realmId = realm.getId();
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        s.realms().removeRealm(realmId);
    }


    @Test
    public void testCredentialCRUD() {
        AtomicReference<String> passwordId = new AtomicReference<>();
        AtomicReference<String> otp1Id = new AtomicReference<>();
        AtomicReference<String> otp2Id = new AtomicReference<>();

        withRealm(realmId, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");
            List<CredentialModel> list = user.credentialManager().getStoredCredentialsStream()
                .collect(Collectors.toList());
            assertEquals(1, list.size());
            passwordId.set(list.get(0).getId());

            // Create 2 OTP credentials (password was already created)
            CredentialModel otp1 = OTPCredentialModel.createFromPolicy(realm, "secret1");
            CredentialModel otp2 = OTPCredentialModel.createFromPolicy(realm, "secret2");
            otp1 = user.credentialManager().createStoredCredential(otp1);
            otp2 = user.credentialManager().createStoredCredential(otp2);
            otp1Id.set(otp1.getId());
            otp2Id.set(otp2.getId());

            return null;
        });


        withRealm(realmId, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: password, otp1, otp2
            List<CredentialModel> list = user.credentialManager().getStoredCredentialsStream()
                .collect(Collectors.toList());
            assertOrder(list, passwordId.get(), otp1Id.get(), otp2Id.get());

            // Assert can't move password when newPreviousCredential not found
            assertFalse(user.credentialManager().moveStoredCredentialTo(passwordId.get(), "not-known"));

            // Assert can't move credential when not found
            assertFalse(user.credentialManager().moveStoredCredentialTo("not-known", otp2Id.get()));

            // Move otp2 up 1 position
            assertTrue(user.credentialManager().moveStoredCredentialTo(otp2Id.get(), passwordId.get()));

            return null;
        });

        withRealm(realmId, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: password, otp2, otp1
            List<CredentialModel> list = user.credentialManager().getStoredCredentialsStream()
                .collect(Collectors.toList());
            assertOrder(list, passwordId.get(), otp2Id.get(), otp1Id.get());

            // Move otp2 to the top
            assertTrue(user.credentialManager().moveStoredCredentialTo(otp2Id.get(), null));

            return null;
        });

        withRealm(realmId, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: otp2, password, otp1
            List<CredentialModel> list = user.credentialManager().getStoredCredentialsStream()
                .collect(Collectors.toList());
            assertOrder(list, otp2Id.get(), passwordId.get(), otp1Id.get());

            // Move password down
            assertTrue(user.credentialManager().moveStoredCredentialTo(passwordId.get(), otp1Id.get()));

            return null;
        });

        withRealm(realmId, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: otp2, otp1, password
            List<CredentialModel> list = user.credentialManager().getStoredCredentialsStream()
                .collect(Collectors.toList());
            assertOrder(list, otp2Id.get(), otp1Id.get(), passwordId.get());

            // Remove otp2 down two positions
            assertTrue(user.credentialManager().moveStoredCredentialTo(otp2Id.get(), passwordId.get()));

            return null;
        });

        withRealm(realmId, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: otp2, otp1, password
            List<CredentialModel> list = user.credentialManager().getStoredCredentialsStream()
                .collect(Collectors.toList());
            assertOrder(list, otp1Id.get(), passwordId.get(), otp2Id.get());

            // Remove password
            assertTrue(user.credentialManager().removeStoredCredentialById(passwordId.get()));

            return null;
        });

        withRealm(realmId, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: otp2, password
            List<CredentialModel> list = user.credentialManager().getStoredCredentialsStream()
                .collect(Collectors.toList());
            assertOrder(list, otp1Id.get(), otp2Id.get());

            return null;
        });
    }


    private void assertOrder(List<CredentialModel> creds, String... expectedIds) {
        assertEquals(expectedIds.length, creds.size());

        if (creds.size() == 0) return;

        for (int i = 0; i < expectedIds.length; i++) {
            assertEquals(creds.get(i).getId(), expectedIds[i]);
        }
    }

}
