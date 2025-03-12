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

package de.arbeitsagentur.opdt.keycloak.compatibility;

import java.util.*;
import java.util.function.Predicate;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.crypto.PublicKeysWrapper;
import org.keycloak.keys.PublicKeyLoader;
import org.keycloak.keys.PublicKeyStorageProvider;

public class TransientPublicKeyStorageProvider implements PublicKeyStorageProvider {

    @Override
    public KeyWrapper getFirstPublicKey(String modelKey, String algorithm, PublicKeyLoader loader) {
        return getPublicKey(modelKey, null, algorithm, loader);
    }

    @Override
    public KeyWrapper getPublicKey(String modelKey, String kid, String algorithm, PublicKeyLoader loader) {
        PublicKeysWrapper keys = null;
        try {
            keys = loader.loadKeys();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return keys.getKeyByKidAndAlg(kid, algorithm);
    }

    @Override
    public KeyWrapper getFirstPublicKey(String modelKey, Predicate<KeyWrapper> predicate, PublicKeyLoader loader) {
        try {
            return loader.loadKeys().getKeyByPredicate(predicate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<KeyWrapper> getKeys(String modelKey, PublicKeyLoader loader) {
        try {
            return loader.loadKeys().getKeys();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean reloadKeys(String modelKey, PublicKeyLoader loader) {
        // noop
        return true;
    }

    @Override
    public void close() {
        // noop
    }
}
