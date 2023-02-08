package de.arbeitsagentur.opdt.keycloak.cassandra.userFederation;

import java.util.Map;
import java.util.stream.Stream;

import org.keycloak.models.GroupModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

public class CassandraUserStorageProvider implements UserLookupProvider, UserQueryProvider, UserRegistrationProvider, UserStorageProvider{

    @Override
    public UserModel addUser(RealmModel realm, String username) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean removeUser(RealmModel realm, UserModel user) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, String search, Integer firstResult,
            Integer maxResults) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params, Integer firstResult,
            Integer maxResults) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group, Integer firstResult,
            Integer maxResults) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
        
    }

    
}
