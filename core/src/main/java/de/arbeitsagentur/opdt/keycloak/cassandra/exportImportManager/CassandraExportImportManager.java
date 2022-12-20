package de.arbeitsagentur.opdt.keycloak.cassandra.exportImportManager;

import org.keycloak.exportimport.ExportAdapter;
import org.keycloak.exportimport.ExportOptions;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.map.datastore.MapExportImportManager;
import org.keycloak.models.utils.StripSecretsUtils;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.util.JsonSerialization;

public class CassandraExportImportManager extends MapExportImportManager {
    private final KeycloakSession session;

    public CassandraExportImportManager(KeycloakSession session) {
        super(session);
        this.session = session;
    }

    @Override
    public void exportRealm(RealmModel realm, ExportOptions options, ExportAdapter callback) {
        callback.setType("application/json");
        callback.writeToOutputStream((outputStream) -> {
            RealmRepresentation rep = CassandraExportUtils.exportRealm(session, realm, options, false);
            StripSecretsUtils.stripForExport(this.session, rep);
            JsonSerialization.writeValueToStream(outputStream, rep);
            outputStream.close();
        });
    }

}