/*
 * Copyright 2024 IT-Systemhaus der Bundesagentur fuer Arbeit
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

import org.keycloak.provider.InvalidationHandler;

public enum MapProviderObjectType implements InvalidationHandler.InvalidableObjectType {
    CLIENT_BEFORE_REMOVE,
    CLIENT_AFTER_REMOVE,
    CLIENT_SCOPE_BEFORE_REMOVE,
    CLIENT_SCOPE_AFTER_REMOVE,
    GROUP_BEFORE_REMOVE,
    GROUP_AFTER_REMOVE,
    REALM_BEFORE_REMOVE,
    REALM_AFTER_REMOVE,
    RESOURCE_SERVER_BEFORE_REMOVE,
    RESOURCE_SERVER_AFTER_REMOVE,
    ROLE_BEFORE_REMOVE,
    ROLE_AFTER_REMOVE,
    USER_BEFORE_REMOVE,
    USER_AFTER_REMOVE
}
