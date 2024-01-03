/*
 * Copyright 2024 Phase Two, Inc.
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
package de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.*;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.google.common.base.Strings;

public class QueryProviders {
  public static Select field(Select select, String name, String value) {
    if (!Strings.isNullOrEmpty(value)) {
      select = select.whereColumn(name).isEqualTo(bindMarker(name));
    }
    return select;
  }

  public static BoundStatementBuilder bind(BoundStatementBuilder boundStatementBuilder, String name, String value) {
    if (!Strings.isNullOrEmpty(value)) {
      boundStatementBuilder = boundStatementBuilder.setString(name, value);
    }
    return boundStatementBuilder;
  }
}
