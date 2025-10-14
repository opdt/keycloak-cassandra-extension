package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.parameters;

import java.util.HashMap;
import java.util.Map;
import org.keycloak.common.Profile;

public class ProfileTestUtils {
    public static void enableTransientUsers() {
        Map<Profile.Feature, Boolean> newFeatures = new HashMap<>();
        for (Profile.Feature feature : Profile.getInstance().getAllFeatures()) {
            newFeatures.put(feature, Profile.isFeatureEnabled(feature));
        }
        newFeatures.put(Profile.Feature.TRANSIENT_USERS, true);

        Profile.init(Profile.ProfileName.DEFAULT, newFeatures);
    }
}
