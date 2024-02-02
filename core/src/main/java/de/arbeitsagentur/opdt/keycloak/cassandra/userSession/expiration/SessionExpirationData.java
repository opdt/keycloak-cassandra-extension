/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
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

package de.arbeitsagentur.opdt.keycloak.cassandra.userSession.expiration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.keycloak.models.RealmModel;

@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SessionExpirationData {
    private RealmModel realm;
    private Integer maxLifespanOverride;
    private Integer offlineMaxLifespanOverride;
    private Integer clientMaxLifespanOverride;
    private Integer offlineClientMaxLifespanOverride;
    private Integer idleTimeoutOverride;
    private Integer offlineIdleTimeoutOverride;
    private Integer clientIdleTimeoutOverride;
    private Integer offlineClientIdleTimeoutOverride;

    public int getOfflineSessionMaxLifespan() {
        return getEffectiveLifespan(offlineMaxLifespanOverride, realm.getOfflineSessionMaxLifespan());
    }

    public int getSsoSessionMaxLifespan() {
        return getEffectiveLifespan(maxLifespanOverride, realm.getSsoSessionMaxLifespan());
    }

    public int getSsoSessionMaxLifespanRememberMe() {
        return realm.getSsoSessionMaxLifespanRememberMe();
    }

    public int getOfflineSessionIdleTimeout() {
        return getEffectiveLifespan(offlineIdleTimeoutOverride, realm.getOfflineSessionIdleTimeout());
    }

    public boolean isOfflineSessionMaxLifespanEnabled() {
        return realm.isOfflineSessionMaxLifespanEnabled();
    }

    public int getSsoSessionIdleTimeoutRememberMe() {
        return realm.getSsoSessionIdleTimeoutRememberMe();
    }

    public int getSsoSessionIdleTimeout() {
        return getEffectiveLifespan(idleTimeoutOverride, realm.getSsoSessionIdleTimeout());
    }

    public int getClientOfflineSessionMaxLifespan() {
        return getEffectiveLifespan(offlineClientMaxLifespanOverride, realm.getClientOfflineSessionMaxLifespan());
    }

    public int getClientOfflineSessionIdleTimeout() {
        return getEffectiveLifespan(offlineClientIdleTimeoutOverride, realm.getClientOfflineSessionIdleTimeout());
    }
    public int getClientSessionIdleTimeout() {
        return getEffectiveLifespan(clientIdleTimeoutOverride, realm.getClientSessionIdleTimeout());
    }

    public int getClientSessionMaxLifespan() {
        return getEffectiveLifespan(clientMaxLifespanOverride, realm.getClientSessionMaxLifespan());
    }

    private int getEffectiveLifespan(Integer override, int realmLifespan) {
        return override == null || override > realmLifespan ? realmLifespan : override;
    }
}
