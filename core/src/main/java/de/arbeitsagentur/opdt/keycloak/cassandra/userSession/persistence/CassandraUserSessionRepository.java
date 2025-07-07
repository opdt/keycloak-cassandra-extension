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
package de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence;

import static org.keycloak.models.UserSessionModel.CORRESPONDING_SESSION_ID;
import static org.keycloak.models.UserSessionModel.SessionPersistenceState.PERSISTENT;

import de.arbeitsagentur.opdt.keycloak.cassandra.StreamExtensions;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AttributeToUserSessionMapping;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSessionValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSessionToAttributeMapping;
import de.arbeitsagentur.opdt.keycloak.common.TimeAdapter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.common.util.Time;
import org.keycloak.models.RealmModel;

@JBossLog
@RequiredArgsConstructor
public class CassandraUserSessionRepository extends TransactionalRepository implements UserSessionRepository {
    private static final String CLIENT_IDS = "clientIds";
    private static final String USER_ID = "userId";
    private static final String BROKER_USER_ID = "brokerUserId";
    private static final String BROKER_SESSION_ID = "brokerSessionId";

    private final boolean transactional;
    private final UserSessionDao dao;
    private final UserSessionAuxiliaryDao auxiliaryDao;

    @Override
    public void update(UserSession session) {
        insertOrUpdateInternal(session);
    }

    @Override
    public void insert(RealmModel realm, UserSession session) {
        insertOrUpdateInternal(session);

        // all these attributes cannot be changed afterwards so they are only set during "insert"
        if (session.getNotes().containsKey(CORRESPONDING_SESSION_ID)) {
            insertOrUpdateInternal(
                    realm,
                    session,
                    new UserSessionToAttributeMapping(
                            session.getId(),
                            CORRESPONDING_SESSION_ID,
                            Arrays.asList(session.getNotes().get(CORRESPONDING_SESSION_ID))));
            session.getNotes().put(CORRESPONDING_SESSION_ID, session.getNotes().get(CORRESPONDING_SESSION_ID));
        }

        if (session.getUserId() != null) {
            insertOrUpdateInternal(
                    realm,
                    session,
                    new UserSessionToAttributeMapping(session.getId(), USER_ID, Arrays.asList(session.getUserId())));
        }

        if (session.getBrokerUserId() != null) {
            insertOrUpdateInternal(
                    realm,
                    session,
                    new UserSessionToAttributeMapping(
                            session.getId(), BROKER_USER_ID, Arrays.asList(session.getBrokerUserId())));
        }

        if (session.getBrokerSessionId() != null) {
            insertOrUpdateInternal(
                    realm,
                    session,
                    new UserSessionToAttributeMapping(
                            session.getId(), BROKER_SESSION_ID, Arrays.asList(session.getBrokerSessionId())));
        }
    }

    @Override
    public void addClientSession(RealmModel realm, UserSession session, AuthenticatedClientSessionValue clientSession) {
        session.getClientSessions().put(clientSession.getClientId(), clientSession);

        UserSessionToAttributeMapping clientIdsAttribute =
                new UserSessionToAttributeMapping(session.getId(), CLIENT_IDS, new ArrayList<>());

        for (String clientId : session.getClientSessions().keySet()) {
            clientIdsAttribute.getAttributeValues().add(clientId);
        }

        if (!clientIdsAttribute.getAttributeValues().isEmpty()) {
            insertOrUpdateInternal(realm, session, clientIdsAttribute);
        }

        insertOrUpdateInternal(session);
    }

    @Override
    public UserSession findUserSessionById(String id) {
        return dao.findById(id);
    }

    @Override
    public List<UserSession> findAll() {
        return dao.findAll().all();
    }

    @Override
    public List<UserSession> findUserSessionsByBrokerSession(String brokerSessionId) {
        return findUserSessionsByAttribute(BROKER_SESSION_ID, brokerSessionId);
    }

    @Override
    public List<UserSession> findUserSessionsByUserId(String userId) {
        return findUserSessionsByAttribute(USER_ID, userId);
    }

    @Override
    public List<UserSession> findUserSessionsByClientId(String clientId) {
        return findUserSessionsByAttribute(CLIENT_IDS, clientId);
    }

    @Override
    public List<UserSession> findUserSessionsByBrokerUserId(String brokerUserId) {
        return findUserSessionsByAttribute(BROKER_USER_ID, brokerUserId);
    }

    @Override
    public void deleteUserSession(UserSession session) {
        if (session == null) {
            return;
        }

        if (transactional) {
            dao.deleteLwt(session);
        } else {
            dao.deleteUserSession(session);
        }

        // Attributes
        if (session.getOffline() != null && session.getOffline()) {
            for (UserSessionToAttributeMapping attribute : auxiliaryDao.findAllAttributes(session.getId())) {
                if (!attribute
                        .getAttributeName()
                        .equals(CORRESPONDING_SESSION_ID)) { // enabled cross-session lookups even after deletion
                    for (String attributeValue : attribute.getAttributeValues()) {
                        auxiliaryDao.deleteAttributeToUserSessionMapping(
                                attribute.getAttributeName(), attributeValue, session.getId());
                    }
                }
            }

            auxiliaryDao.findAllAttributes(session.getId()).all().stream()
                    .filter(s -> !s.getAttributeName().equals(CORRESPONDING_SESSION_ID))
                    .forEach(s -> auxiliaryDao.deleteAttribute(s.getUserSessionId(), s.getAttributeName()));
        } else {
            for (UserSessionToAttributeMapping attribute : auxiliaryDao.findAllAttributes(session.getId())) {
                for (String attributeValue : attribute.getAttributeValues()) {
                    if (!attribute
                            .getAttributeName()
                            .equals(CORRESPONDING_SESSION_ID)) { // enabled cross-session lookups even after deletion
                        auxiliaryDao.deleteAttributeToUserSessionMapping(
                                attribute.getAttributeName(), attributeValue, session.getId());
                    }
                }
            }

            auxiliaryDao.findAllAttributes(session.getId()).all().stream()
                    .filter(s -> !s.getAttributeName().equals(CORRESPONDING_SESSION_ID))
                    .forEach(s -> auxiliaryDao.deleteAttribute(s.getUserSessionId(), s.getAttributeName()));
        }
    }

    @Override
    public void deleteUserSession(String id) {
        deleteUserSession(findUserSessionById(id));
    }

    @Override
    public void deleteCorrespondingUserSession(UserSession session) {
        if (!session.getNotes().containsKey(CORRESPONDING_SESSION_ID)) {
            return;
        }

        deleteUserSession(session.getNotes().get(CORRESPONDING_SESSION_ID));
    }

    // Attributes
    @Override
    public Set<String> findUserSessionIdsByAttribute(String name, String value, int firstResult, int maxResult) {
        return StreamExtensions.paginated(auxiliaryDao.findByAttribute(name, value), firstResult, maxResult)
                .map(AttributeToUserSessionMapping::getUserSessionId)
                .collect(Collectors.toSet());
    }

    @Override
    public List<UserSession> findUserSessionsByAttribute(String name, String value) {
        List<String> sessionIds = auxiliaryDao.findByAttribute(name, value).all().stream()
                .map(AttributeToUserSessionMapping::getUserSessionId)
                .collect(Collectors.toList());

        return dao.findByIds(sessionIds).all();
    }

    @Override
    public UserSession findFirstUserSessionByAttribute(String name, String value) {
        List<UserSession> userSessions = findUserSessionsByAttribute(name, value);

        if (userSessions.size() > 1) {
            log.warnf(
                    "Found more than one userSession with attributeName '%s' and value '%s'. Using newest one.",
                    name, value);
        } else if (userSessions.isEmpty()) {
            return null;
        }

        return userSessions.stream()
                .max(Comparator.comparing(UserSession::getTimestamp))
                .orElse(null);
    }

    @Override
    public MultivaluedHashMap<String, String> findAllUserSessionAttributes(String userSessionId) {
        List<UserSessionToAttributeMapping> attributeMappings =
                auxiliaryDao.findAllAttributes(userSessionId).all();
        MultivaluedHashMap<String, String> result = new MultivaluedHashMap<>();

        attributeMappings.forEach(mapping -> result.addAll(mapping.getAttributeName(), mapping.getAttributeValues()));

        return result;
    }

    @Override
    public UserSessionToAttributeMapping findUserSessionAttribute(String userSessionId, String attributeName) {
        return auxiliaryDao.findAttribute(userSessionId, attributeName);
    }

    private void insertOrUpdateInternal(UserSession session) {
        if ((session.getOffline() != null && session.getOffline())
                || PERSISTENT.equals(session.getPersistenceState())) {
            if (session.getExpiration() == null) {
                if (transactional) {
                    insertOrUpdateLwt(dao, session, false);
                } else {
                    dao.insertOrUpdate(session);
                }
            } else {
                int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(
                        TimeAdapter.fromMilliSecondsToSeconds(session.getExpiration() - Time.currentTimeMillis()));
                if (transactional) {
                    insertOrUpdateLwt(dao, session, ttl, false);
                } else {
                    dao.insertOrUpdate(session, ttl);
                }
            }
        }
    }

    private void insertOrUpdateInternal(RealmModel realm, UserSession session, UserSessionToAttributeMapping mapping) {
        Integer calculatedTtl = session.getExpiration() == null
                ? null
                : TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(
                        TimeAdapter.fromMilliSecondsToSeconds(session.getExpiration() - Time.currentTimeMillis()));

        if (mapping.getAttributeName().equals(CORRESPONDING_SESSION_ID)) {
            calculatedTtl = realm.getOfflineSessionMaxLifespan() * 2; // Housekeeping
        }

        final Integer ttl = calculatedTtl;

        UserSessionToAttributeMapping oldAttribute =
                auxiliaryDao.findAttribute(mapping.getUserSessionId(), mapping.getAttributeName());

        if (ttl == null) {
            auxiliaryDao.insertOrUpdate(mapping);
        } else {
            auxiliaryDao.insertOrUpdate(mapping, ttl);
        }

        if (oldAttribute != null) {
            // Alte AttributeToUserSessionMappings löschen, da die Values als Teil des PartitionKey nicht
            // geändert werden können
            oldAttribute
                    .getAttributeValues()
                    .forEach(value -> auxiliaryDao.deleteAttributeToUserSessionMapping(
                            oldAttribute.getAttributeName(), value, oldAttribute.getUserSessionId()));
        }

        mapping.getAttributeValues().forEach(value -> {
            AttributeToUserSessionMapping attributeToUserSessionMapping =
                    new AttributeToUserSessionMapping(mapping.getAttributeName(), value, mapping.getUserSessionId());

            if (ttl == null) {
                auxiliaryDao.insert(attributeToUserSessionMapping);
            } else {
                auxiliaryDao.insert(attributeToUserSessionMapping, ttl);
            }
        });
    }
}
