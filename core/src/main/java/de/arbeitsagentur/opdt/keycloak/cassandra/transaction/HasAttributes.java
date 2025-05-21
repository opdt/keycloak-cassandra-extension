package de.arbeitsagentur.opdt.keycloak.cassandra.transaction;

import java.util.List;
import java.util.Map;

public interface HasAttributes {
    Map<String, List<String>> getAttributes();
}
