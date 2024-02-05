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
package de.arbeitsagentur.opdt.keycloak.cassandra;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.core.paging.OffsetPager;
import java.util.stream.Stream;

public final class StreamExtensions {
  public static <T> Stream<T> paginated(
      PagingIterable<T> rs, Integer firstResult, Integer maxResult) {
    if (maxResult == null || maxResult == -1) {
      return rs.all().stream();
    }

    OffsetPager offsetPager = new OffsetPager(maxResult);
    OffsetPager.Page<T> page = offsetPager.getPage(rs, (firstResult / maxResult) + 1);

    return page.getElements().stream();
  }
}
