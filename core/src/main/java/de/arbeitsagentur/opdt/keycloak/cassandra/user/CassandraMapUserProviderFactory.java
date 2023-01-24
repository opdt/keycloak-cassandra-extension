package de.arbeitsagentur.opdt.keycloak.cassandra.user;
import com.google.auto.service.AutoService;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.AbstractCassandraProviderFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.InvalidationHandler;

import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.CLIENT_BEFORE_REMOVE;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.CLIENT_SCOPE_BEFORE_REMOVE;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.GROUP_BEFORE_REMOVE;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.REALM_BEFORE_REMOVE;
import static org.keycloak.models.map.common.AbstractMapProviderFactory.MapProviderObjectType.ROLE_BEFORE_REMOVE;

/**
 *
 * Ported from:
 * @author mhajas
 */
@JBossLog
@AutoService(UserProviderFactory.class)
public class CassandraMapUserProviderFactory extends AbstractCassandraProviderFactory implements UserProviderFactory<CassandraUserProvider>, EnvironmentDependentProviderFactory, InvalidationHandler {

    private static final String PROVIDER_ID = "map";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public CassandraUserProvider create(KeycloakSession session) {
        return new CassandraUserProvider(session, createRepository(session));
    }

    @Override
    public void init(Config.Scope scope) {
        // NOOP
    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {
        // NOOP
    }

    @Override
    public void close() {
        // NOOP
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public void invalidate(KeycloakSession session, InvalidableObjectType type, Object... params) {
        if (type == REALM_BEFORE_REMOVE) {
            create(session).preRemove((RealmModel) params[0]);
        } else if (type == ROLE_BEFORE_REMOVE) {
            create(session).preRemove((RealmModel) params[0], (RoleModel) params[1]);
        } else if (type == CLIENT_SCOPE_BEFORE_REMOVE) {
            create(session).preRemove((ClientScopeModel) params[1]);
        } else if (type == CLIENT_BEFORE_REMOVE) {
            create(session).preRemove((RealmModel) params[0], (ClientModel) params[1]);
        } else if (type == GROUP_BEFORE_REMOVE) {
            create(session).preRemove((RealmModel) params[0], (GroupModel) params[1]);
        }
    }
}
