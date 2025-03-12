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
package de.arbeitsagentur.opdt.keycloak.cassandra.cache;

import java.lang.reflect.Method;
import java.util.Arrays;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@EqualsAndHashCode
@Getter
@RequiredArgsConstructor
public class CacheInvocationContext {
    private final Class targetClass;
    private final String targetMethod;
    private final Object[] args;

    public static CacheInvocationContext create(Object target, Method method, Object[] args) {
        return new CacheInvocationContext(target.getClass(), method.getName(), args);
    }

    @Override
    public String toString() {
        return String.format("%s.%s(%s)", targetClass.getName(), targetMethod, Arrays.toString(args));
    }
}
