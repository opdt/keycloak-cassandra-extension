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

package de.arbeitsagentur.opdt.keycloak.cassandra.transaction;

import java.util.ArrayList;
import java.util.List;

// TODO: Find way to model Version / VersionReadonly attributes in a generic manner
public abstract class TransactionalModelAdapter implements CassandraModelTransaction {
    private boolean updated = false;
    private boolean deleted = false;

    private List<Runnable> postUpdateTasks = new ArrayList<>();

    public abstract String getId();
    public void markUpdated() {
        updated = true;
    }

    public void markUpdated(Runnable postUpdateTask) {
        updated = true;
        postUpdateTasks.add(postUpdateTask);
    }

    public void markDeleted() {
        deleted = true;
    }


    @Override
    public void commit() {
        if (updated && !deleted) {
            flushChanges();

            postUpdateTasks.forEach(Runnable::run);
            postUpdateTasks.clear();
            updated = false;
        }
    }

    protected abstract void flushChanges();
}
