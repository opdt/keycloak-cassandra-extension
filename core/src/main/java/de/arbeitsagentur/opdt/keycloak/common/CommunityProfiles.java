/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.arbeitsagentur.opdt.keycloak.common;

public class CommunityProfiles {
    private static final String ENV_CASSANDRA_PROFILE_ENABLED = "KC_COMMUNITY_DATASTORE_CASSANDRA_ENABLED";
    private static final String PROP_CASSANDRA_PROFILE_ENABLED = "kc.community.datastore.cassandra.enabled";
    private static final String ENV_CASSANDRA_CACHE_PROFILE_ENABLED = "KC_COMMUNITY_DATASTORE_CASSANDRA_CACHE_ENABLED";
    private static final String PROP_CASSANDRA_CACHE_PROFILE_ENABLED = "kc.community.datastore.cassandra.cache.enabled";

    private static final boolean isCassandraProfileEnabled;
    private static final boolean isCassandraCacheProfileEnabled;

    static {
        isCassandraProfileEnabled = Boolean.parseBoolean(System.getenv(ENV_CASSANDRA_PROFILE_ENABLED)) || Boolean.parseBoolean(System.getProperty(PROP_CASSANDRA_PROFILE_ENABLED));
        isCassandraCacheProfileEnabled = Boolean.parseBoolean(System.getenv(ENV_CASSANDRA_CACHE_PROFILE_ENABLED)) || Boolean.parseBoolean(System.getProperty(PROP_CASSANDRA_CACHE_PROFILE_ENABLED));
    }


    public static boolean isCassandraProfileEnabled() {
        return isCassandraProfileEnabled;
    }

    public static boolean isCassandraCacheProfileEnabled() {
        return isCassandraCacheProfileEnabled;
    }
}
