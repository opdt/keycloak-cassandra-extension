package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import org.keycloak.device.DeviceRepresentationProvider;
import org.keycloak.device.DeviceRepresentationProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.account.DeviceRepresentation;

public class NullDeviceRepresentationProviderFactory implements DeviceRepresentationProviderFactory {
    public static String PROVIDER_ID = "test";

    @Override
    public DeviceRepresentationProvider create(KeycloakSession session) {
        return new DeviceRepresentationProvider() {
            @Override
            public DeviceRepresentation deviceRepresentation() {
                return new DeviceRepresentation();
            }
        };
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
