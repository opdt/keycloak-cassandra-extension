/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
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

package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import jnr.posix.WString;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.models.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.empty;

@RequireProvider(UserProvider.class)
@RequireProvider(RealmProvider.class)
@RequireProvider(GroupProvider.class)
public class GroupModelTest extends KeycloakModelTest {
    private String realmId;
    private String firstGroupId;
    private String secondGroupId;
    private String thirdGroupId;
    private static final String OLD_VALUE = "oldValue";
    private static final String NEW_VALUE = "newValue";

    @Override
    public void createEnvironment(KeycloakSession s) {
        RealmModel realm = s.realms().createRealm("original");
        realm.setDefaultRole(s.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
        realmId = realm.getId();

        UserModel john = s.users().addUser(realm, "john");
        UserModel mary = s.users().addUser(realm, "mary");

        firstGroupId = s.groups().createGroup(realm, "firstGroup").getId();
        secondGroupId = s.groups().createGroup(realm, "secondGroup").getId();
        thirdGroupId = s.groups().createGroup(realm, "thirdGroup").getId();
    }

    @Override
    public void cleanEnvironment(KeycloakSession s) {
        s.realms().removeRealm(realmId);
    }

    @Test
    public void testGroupUniqueness() {
        withRealm(realmId, (session, realm) -> {
            GroupModel secondGroup = session.groups().getGroupById(realm, secondGroupId);

            GroupProvider groupProvider = session.groups();
            Assert.assertThrows(ModelDuplicateException.class,
                () -> groupProvider.createGroup(realm, "firstGroup"));

            GroupModel subGroup = session.groups().createGroup(realm, "subGroup", secondGroup);
            Assert.assertThrows(ModelDuplicateException.class,
                () -> groupProvider.createGroup(realm, "subGroup", secondGroup));

            return null;
        });
    }

    @Test
    public void testBasicCreateRemoveGroup() {
        withRealm(realmId, (session, realm) -> {
            GroupModel secondGroup = session.groups().getGroupById(realm, secondGroupId);

            GroupModel subGroup = session.groups().createGroup(realm, "firstGroup", secondGroup);

            Assert.assertEquals(4, session.groups().getGroupsCount(realm, false), 0);
            Assert.assertEquals(3, session.groups().getGroupsCount(realm, true), 0);

            session.groups().removeGroup(realm, subGroup);
            Assert.assertEquals(3, session.groups().getGroupsCount(realm, false), 0);

            GroupModel fourthGroup = session.groups().createGroup(realm, "fourthGroupId", "fourthGroup");
            Assert.assertEquals("fourthGroup", session.groups().getGroupById(realm, "fourthGroupId").getName());

            Assert.assertFalse(session.groups().removeGroup(realm, null));

            return null;
        });
    }

    @Test
    public void testBasicGroupModel() {
        withRealm(realmId, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, firstGroupId);
            firstGroup.setName("veryFirstGroup");

            firstGroup.setSingleAttribute("key1", "value1");
            firstGroup.setAttribute("key2", Arrays.asList("value21", "value22", "value23"));


            return null;
        });

        withRealm(realmId, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, firstGroupId);
            Assert.assertEquals("veryFirstGroup", firstGroup.getName());

            Assert.assertEquals(2, firstGroup.getAttributes().size());
            Assert.assertEquals("veryFirstGroup", firstGroup.getName());
            Assert.assertTrue(firstGroup.getAttributeStream("key2").allMatch(value -> value.contains("value2")));
            Assert.assertEquals("value21", firstGroup.getFirstAttribute("key2"));

            firstGroup.removeAttribute("key1");
            return null;
        });

        withRealm(realmId, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, firstGroupId);
            Assert.assertEquals(1, firstGroup.getAttributes().size());
            return null;
        });
    }

    @Test
    public void testAddRemoveGroupChild() {
        withRealm(realmId, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, firstGroupId);
            GroupModel secondGroup = session.groups().getGroupById(realm, secondGroupId);

            firstGroup.addChild(secondGroup);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, firstGroupId);
            GroupModel secondGroup = session.groups().getGroupById(realm, secondGroupId);

            Assert.assertEquals(1, firstGroup.getSubGroupsStream().count());
            Assert.assertEquals(firstGroupId, secondGroup.getParentId());

            firstGroup.removeChild(secondGroup);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, firstGroupId);

            Assert.assertEquals(0, firstGroup.getSubGroupsStream().count());

            return null;
        });
    }

    @Test
    public void testGroupRoleMapping() {
        withRealm(realmId, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, firstGroupId);
            GroupModel secondGroup = session.groups().getGroupById(realm, secondGroupId);

            ClientModel client = session.clients().addClient(realm, "client-id");

            RoleModel realmRole1 = session.roles().addRealmRole(realm, "realmRole1");
            RoleModel realmRole2 = session.roles().addRealmRole(realm, "realmRole2");
            RoleModel realmRole3 = session.roles().addRealmRole(realm, "realmRole3");
            realmRole1.addCompositeRole(realmRole2);

            RoleModel clientRole1 = session.roles().addClientRole(client, "clientRole1");
            RoleModel clientRole2 = session.roles().addClientRole(client, "clientRole2");
            clientRole1.addCompositeRole(clientRole2);

            firstGroup.grantRole(realmRole1);
            firstGroup.grantRole(realmRole3);

            secondGroup.grantRole(clientRole1);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, firstGroupId);
            GroupModel secondGroup = session.groups().getGroupById(realm, secondGroupId);
            ClientModel client = session.clients().getClientByClientId(realm, "client-id");

            Assert.assertEquals(2, firstGroup.getRealmRoleMappingsStream().count());
            Assert.assertEquals(0, firstGroup.getClientRoleMappingsStream(client).count());
            Assert.assertEquals(1, secondGroup.getClientRoleMappingsStream(client).count());

            Assert.assertTrue(firstGroup.hasRole(session.roles().getRealmRole(realm, "realmRole2")));
            Assert.assertFalse(secondGroup.hasRole(session.roles().getRealmRole(realm, "realmRole2")));

            Assert.assertTrue(secondGroup.hasRole(session.roles().getClientRole(client,"clientRole2")));
            Assert.assertFalse(firstGroup.hasRole(session.roles().getClientRole(client,"clientRole2")));


            Assert.assertTrue(firstGroup.hasDirectRole(session.roles().getRealmRole(realm, "realmRole3")));
            Assert.assertFalse(firstGroup.hasDirectRole(session.roles().getRealmRole(realm, "realmRole2")));

            Assert.assertTrue(secondGroup.hasDirectRole(session.roles().getClientRole(client, "clientRole1")));
            Assert.assertFalse(secondGroup.hasDirectRole(session.roles().getClientRole(client, "clientRole2")));

            return null;
        });
    }

    @Test
    public void testSearchForGroupByNameStream() {
        withRealm(realmId, (session, realm) -> {
            Assert.assertEquals(1, session.groups().searchForGroupByNameStream(realm, "firstGroup", true, null, null).count());
            Assert.assertEquals(0, session.groups().searchForGroupByNameStream(realm, "firstgroup", true, null, null).count());
            Assert.assertEquals(1, session.groups().searchForGroupByNameStream(realm, "firstgroup", false, null, null).count());

            Assert.assertEquals(3, session.groups().searchForGroupByNameStream(realm, "Gr", false, null, null).count());
            Assert.assertEquals(0, session.groups().searchForGroupByNameStream(realm, "Gr", true, null, null).count());

            Assert.assertEquals(2, session.groups().searchForGroupByNameStream(realm, "Gr", false, 1, -1).count());
            Assert.assertEquals(2, session.groups().searchForGroupByNameStream(realm, "Gr", false, -1, 2).count());
            Assert.assertEquals(1, session.groups().searchForGroupByNameStream(realm, "Gr", false, 2, 10).count());

            return null;
        });
    }

    @Test
    public void testSearchByNameWithHierarchy() {
        withRealm(realmId, (session, realm) -> {
            session.groups().createGroup(realm, "deepGroup",
                session.groups().createGroup(realm, "subGroup", session.groups().getGroupById(realm, secondGroupId)));
            session.groups().createGroup(realm, "subGroup", session.groups().getGroupById(realm, thirdGroupId));

            Assert.assertEquals(1, session.groups().searchForGroupByNameStream(realm, "deepGroup", true, null, null).count());
            Assert.assertEquals(2, session.groups().searchForGroupByNameStream(realm, "subGroup", true, null, null).count());
            Assert.assertEquals(3, session.groups().searchForGroupByNameStream(realm, "Group", false, null, null).count());

            return null;
        });
    }

    @Test
    public void testGetGroupsStream() {
        withRealm(realmId, (session, realm) -> {
            Assert.assertEquals(3, session.groups().getGroupsStream(realm).count());
            Assert.assertEquals(3, session.groups().getGroupsStream(realm, Stream.of(firstGroupId, secondGroupId, thirdGroupId), "", null, null).count());

            Assert.assertEquals(2, session.groups().getGroupsStream(realm, Stream.of(firstGroupId, secondGroupId), "Group", null, null).count());
            Assert.assertEquals(2, session.groups().getGroupsStream(realm, Stream.of(firstGroupId, secondGroupId), "group", null, null).count());
            Assert.assertEquals(1, session.groups().getGroupsStream(realm, Stream.of(firstGroupId, secondGroupId), "first", null, null).count());

            Assert.assertEquals(3, session.groups().getGroupsStream(realm, Stream.of(firstGroupId, secondGroupId, thirdGroupId), "Group", null, null).count());
            Assert.assertEquals(2, session.groups().getGroupsStream(realm, Stream.of(firstGroupId, secondGroupId, thirdGroupId), "Group", -1, 2).count());
            Assert.assertEquals(2, session.groups().getGroupsStream(realm, Stream.of(firstGroupId, secondGroupId, thirdGroupId), "Group", 1, -1).count());
            Assert.assertEquals(1, session.groups().getGroupsStream(realm, Stream.of(firstGroupId, secondGroupId, thirdGroupId), "Group", 2, 10).count());

            return null;
        });
    }

    @Test
    public void testGetGroupsCount() {
        withRealm(realmId, (session, realm) -> {
            session.groups().createGroup(realm, "subGroup", session.groups().getGroupById(realm, thirdGroupId));

            Assert.assertEquals(3, session.groups().getGroupsCount(realm, true), 0);
            Assert.assertEquals(4, session.groups().getGroupsCount(realm, false), 0);

            Assert.assertEquals(3, session.groups().getGroupsCountByNameContaining(realm, "Group"), 0);
            Assert.assertEquals(3, session.groups().getGroupsCountByNameContaining(realm, "group"), 0);
            Assert.assertEquals(1, session.groups().getGroupsCountByNameContaining(realm, "first"), 0);

            return null;
        });
    }

    @Test
    public void testGetTopLevelGroupsStream() {
        withRealm(realmId, (session, realm) -> {
            session.groups().moveGroup(realm, session.groups().getGroupById(realm, firstGroupId), session.groups().getGroupById(realm, firstGroupId));
            session.groups().moveGroup(realm, session.groups().getGroupById(realm, thirdGroupId), session.groups().getGroupById(realm, secondGroupId));

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            Assert.assertNull(session.groups().getGroupById(realm, firstGroupId).getParentId());
            Assert.assertEquals(2, session.groups().getTopLevelGroupsStream(realm).count());

            session.groups().addTopLevelGroup(realm, session.groups().getGroupById(realm, thirdGroupId));
            return null;
        });

        withRealm(realmId, (session, realm) -> {
            Assert.assertEquals(3, session.groups().getTopLevelGroupsStream(realm).count());

            Assert.assertEquals(3, session.groups().getTopLevelGroupsStream(realm, null, null).count());
            Assert.assertEquals(2, session.groups().getTopLevelGroupsStream(realm, -1, 2).count());
            Assert.assertEquals(2, session.groups().getTopLevelGroupsStream(realm, 1, -1).count());
            Assert.assertEquals(1, session.groups().getTopLevelGroupsStream(realm, 2, 10).count());

            GroupModel secondGroup = session.groups().getGroupById(realm, secondGroupId);
            GroupModel thirdGroup = session.groups().getGroupById(realm, thirdGroupId);

            GroupProvider groupProvider = session.groups();
            groupProvider.moveGroup(realm, thirdGroup, secondGroup);
            GroupModel thirdGroupWithoutParent = groupProvider.createGroup(realm, "thirdGroup");

            Assert.assertThrows(ModelDuplicateException.class,
                () -> groupProvider.moveGroup(realm, thirdGroupWithoutParent, secondGroup));
            return null;
        });
    }

    @Test
    public void testGetGroupsByRoleStream() {
        withRealm(realmId, (session, realm) -> {
            GroupModel group1 = session.groups().getGroupById(realm, firstGroupId);
            GroupModel group2 = session.groups().getGroupById(realm, secondGroupId);
            GroupModel group3 = session.groups().getGroupById(realm, thirdGroupId);

            RoleModel role1 = session.roles().addRealmRole(realm, "role1");
            RoleModel role2 = session.roles().addRealmRole(realm, "role2");
            RoleModel role3 = session.roles().addRealmRole(realm, "role3");

            group1.grantRole(role1);

            group2.grantRole(role1);
            group2.grantRole(role2);

            group3.grantRole(role1);
            group3.grantRole(role2);
            group3.grantRole(role3);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            GroupModel group1 = session.groups().getGroupById(realm, firstGroupId);
            GroupModel group2 = session.groups().getGroupById(realm, secondGroupId);
            GroupModel group3 = session.groups().getGroupById(realm, thirdGroupId);

            RoleModel role1 = session.roles().getRealmRole(realm, "role1");
            RoleModel role2 = session.roles().getRealmRole(realm, "role2");
            RoleModel role3 = session.roles().getRealmRole(realm, "role3");

            List<GroupModel> groups = session.groups().getGroupsByRoleStream(realm, role1, null, null)
                .collect(Collectors.toList());;
            Assert.assertThat(groups, hasSize(3));
            Assert.assertThat(groups, containsInAnyOrder(group1, group2, group3));

            groups = session.groups().getGroupsByRoleStream(realm, role2, null, null)
                .collect(Collectors.toList());;
            Assert.assertThat(groups, hasSize(2));
            Assert.assertThat(groups, containsInAnyOrder(group2, group3));

            groups = session.groups().getGroupsByRoleStream(realm, role3, null, null)
                .collect(Collectors.toList());;
            Assert.assertThat(groups, hasSize(1));
            Assert.assertThat(groups, containsInAnyOrder(group3));

            groups = session.groups().getGroupsByRoleStream(realm, role1, -1, 2)
                .collect(Collectors.toList());;
            Assert.assertEquals(2, groups.size());

            groups = session.groups().getGroupsByRoleStream(realm, role1, 1, -1)
                .collect(Collectors.toList());;
            Assert.assertEquals(2, groups.size());

            groups = session.groups().getGroupsByRoleStream(realm, role1, 2, 10)
                .collect(Collectors.toList());;
            Assert.assertEquals(1, groups.size());

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            RoleModel role1 = session.roles().getRealmRole(realm, "role1");
            session.roles().removeRole(role1);

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            GroupModel group1 = session.groups().getGroupById(realm, firstGroupId);
            GroupModel group2 = session.groups().getGroupById(realm, secondGroupId);
            GroupModel group3 = session.groups().getGroupById(realm, thirdGroupId);

            Assert.assertEquals(0, group1.getRealmRoleMappingsStream().count());
            Assert.assertEquals(1, group2.getRealmRoleMappingsStream().count());
            Assert.assertEquals(2, group3.getRealmRoleMappingsStream().count());

            return null;
        });
    }

    @Test
    public void testSearchGroupsByAttributes() {
        withRealm(realmId, (session, realm) -> {
            GroupModel group1 = session.groups().getGroupById(realm, firstGroupId);
            GroupModel group2 = session.groups().getGroupById(realm, secondGroupId);
            GroupModel group3 = session.groups().getGroupById(realm, thirdGroupId);

            group1.setSingleAttribute("key1", "value1");
            group1.setSingleAttribute("key2", "value21");

            group2.setSingleAttribute("key1", "value1");
            group2.setSingleAttribute("key2", "value22");

            group3.setSingleAttribute("key2", "value21");

            return null;
        });

        withRealm(realmId, (session, realm) -> {
            GroupModel group1 = session.groups().getGroupById(realm, firstGroupId);
            GroupModel group2 = session.groups().getGroupById(realm, secondGroupId);
            GroupModel group3 = session.groups().getGroupById(realm, thirdGroupId);

            Map<String, String> attributesToSearch = new HashMap<>();

            attributesToSearch.put("key1", "value1");
            List<GroupModel> groups = session.groups().searchGroupsByAttributes(realm, attributesToSearch, null, null)
                .collect(Collectors.toList());
            Assert.assertThat(groups, hasSize(2));
            Assert.assertThat(groups, containsInAnyOrder(group1, group2));

            attributesToSearch.clear();
            attributesToSearch.put("key2", "value21");
            groups = session.groups().searchGroupsByAttributes(realm, attributesToSearch, null, null)
                .collect(Collectors.toList());
            Assert.assertThat(groups, hasSize(2));
            Assert.assertThat(groups, containsInAnyOrder(group1, group3));

            attributesToSearch.clear();
            attributesToSearch.put("key2", "value22");
            groups = session.groups().searchGroupsByAttributes(realm, attributesToSearch, null, null)
                .collect(Collectors.toList());
            Assert.assertThat(groups, hasSize(1));
            Assert.assertThat(groups, contains(group2));

            attributesToSearch.clear();
            attributesToSearch.put("key3", "value3");
            groups = session.groups().searchGroupsByAttributes(realm, attributesToSearch, null, null)
                .collect(Collectors.toList());
            Assert.assertThat(groups, empty());

            attributesToSearch.clear();
            attributesToSearch.put("key1", "value1");
            attributesToSearch.put("key2", "value21");
            groups = session.groups().searchGroupsByAttributes(realm, attributesToSearch, null, null)
                .collect(Collectors.toList());
            Assert.assertEquals(3, groups.size());
            groups = session.groups().searchGroupsByAttributes(realm, attributesToSearch, -1, 2)
                .collect(Collectors.toList());
            Assert.assertEquals(2, groups.size());
            groups = session.groups().searchGroupsByAttributes(realm, attributesToSearch, 1, -1)
                .collect(Collectors.toList());
            Assert.assertEquals(2, groups.size());
            groups = session.groups().searchGroupsByAttributes(realm, attributesToSearch, 2, 10)
                .collect(Collectors.toList());
            Assert.assertEquals(1, groups.size());

            return null;
        });
    }

    @Test
    public void testGroupAttributesSetter() {
        String groupId = withRealm(realmId, (session, realm) -> {
            GroupModel groupModel = session.groups().createGroup(realm, "my-group");
            groupModel.setSingleAttribute("key", OLD_VALUE);

            return groupModel.getId();
        });
        withRealm(realmId, (session, realm) -> {
            GroupModel groupModel = session.groups().getGroupById(realm, groupId);
            assertThat(groupModel.getAttributes().get("key"), contains(OLD_VALUE));

            // Change value to NEW_VALUE
            groupModel.setSingleAttribute("key", NEW_VALUE);

            // Check all getters return the new value
            assertThat(groupModel.getAttributes().get("key"), contains(NEW_VALUE));
            assertThat(groupModel.getFirstAttribute("key"), equalTo(NEW_VALUE));
            assertThat(groupModel.getAttributeStream("key").findFirst().get(), equalTo(NEW_VALUE));

            return null;
        });
    }
}
