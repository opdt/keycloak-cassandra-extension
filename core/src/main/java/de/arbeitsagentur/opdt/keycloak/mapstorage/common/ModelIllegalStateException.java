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

package de.arbeitsagentur.opdt.keycloak.mapstorage.common;

import org.keycloak.models.ModelException;

public class ModelIllegalStateException extends ModelException {
  public ModelIllegalStateException() {}

  public ModelIllegalStateException(String message) {
    super(message);
  }

  public ModelIllegalStateException(String message, Throwable cause) {
    super(message, cause);
  }

  public ModelIllegalStateException(Throwable cause) {
    super(cause);
  }
}
