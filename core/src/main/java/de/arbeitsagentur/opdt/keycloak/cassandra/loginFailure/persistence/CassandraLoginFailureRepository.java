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
package de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.entities.LoginFailure;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CassandraLoginFailureRepository implements LoginFailureRepository {
  private final LoginFailureDao dao;

  @Override
  public void insertOrUpdate(LoginFailure loginFailure) {
    dao.insertOrUpdate(loginFailure);
  }

  @Override
  public List<LoginFailure> findLoginFailuresByUserId(String userId) {
    return dao.findByUserId(userId).all();
  }

  @Override
  public void deleteLoginFailure(LoginFailure loginFailure) {
    dao.delete(loginFailure);
  }

  @Override
  public void deleteLoginFailureByUserId(String userId) {
    dao.deleteByUserId(userId);
  }

  @Override
  public List<LoginFailure> findAllLoginFailures() {
    return dao.findAll().all();
  }
}
