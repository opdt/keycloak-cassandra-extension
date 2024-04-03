package de.arbeitsagentur.opdt.keycloak.cassandra.group;

import static de.arbeitsagentur.opdt.keycloak.common.MapProviderObjectType.GROUP_AFTER_REMOVE;
import static de.arbeitsagentur.opdt.keycloak.common.MapProviderObjectType.GROUP_BEFORE_REMOVE;

import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.GroupRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.GroupValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.Groups;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.CassandraModelTransaction;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;

@JBossLog
public class CassandraGroupProvider implements GroupProvider {
  private final KeycloakSession session;
  private final GroupRepository groupRepository;

  private final Map<String, Groups> groupsByRealmId = new HashMap<>();
  private final Set<String> groupsChanged = new HashSet<>();
  private final Set<String> groupsDeleted = new HashSet<>();

  public CassandraGroupProvider(KeycloakSession session, GroupRepository groupRepository) {
    this.groupRepository = groupRepository;
    this.session = session;
  }

  public void markChanged(String realmId) {
    groupsChanged.add(realmId);
  }

  public void markDeleted(String realmId) {
    groupsDeleted.add(realmId);
  }

  private Groups getGroups(String realmId) {
    if (groupsByRealmId.containsKey(realmId)) {
      return groupsByRealmId.get(realmId);
    }

    Groups groups = groupRepository.getGroupsByRealmId(realmId);
    groupsByRealmId.put(realmId, groups);

    session
        .getTransactionManager()
        .enlistAfterCompletion(
            (CassandraModelTransaction)
                () -> {
                  if (groupsChanged.contains(realmId) && !groupsDeleted.contains(realmId)) {
                    groupRepository.insertOrUpdate(groups);
                  }

                  groupsByRealmId.remove(realmId);
                  groupsChanged.remove(realmId);
                  groupsDeleted.remove(realmId);
                });

    return groups;
  }

  private Function<GroupValue, GroupModel> entityToAdapterFunc(RealmModel realm) {
    return origEntity ->
        origEntity == null
            ? null
            : new CassandraGroupAdapter(
                origEntity.getId(), session, realm, origEntity, getGroups(realm.getId()), this);
  }

  @Override
  public GroupModel createGroup(RealmModel realm, String id, String name, GroupModel toParent) {
    log.debugv(
        "createGroup(%s, %s, %s, %s)",
        realm.getId(), id, name, toParent == null ? "null" : toParent.getId());

    if (groupExists(realm.getId(), name, toParent)) {
      throw new ModelDuplicateException(
          "Group with the same name or parent id exists:"
              + name
              + " for parent id "
              + (toParent == null ? "null" : toParent.getId()));
    }

    Groups groups = getGroups(realm.getId());

    GroupValue group =
        GroupValue.builder()
            .id(id == null ? KeycloakModelUtils.generateId() : id)
            .name(name)
            .parentId(toParent == null ? null : toParent.getId())
            .build();

    groups.addRealmGroup(group);
    markChanged(realm.getId());
    return entityToAdapterFunc(realm).apply(group);
  }

  @Override
  public Stream<GroupModel> getGroupsStream(RealmModel realm) {
    log.debugf("getGroupsStream: realmId=%s", realm.getId());

    Groups groups = getGroups(realm.getId());

    return groups.getRealmGroups().stream().map(entityToAdapterFunc(realm));
  }

  @Override
  public Stream<GroupModel> getGroupsStream(
      RealmModel realm, Stream<String> ids, String search, Integer first, Integer max) {
    log.debugf(
        "getGroupsStream: realmId=%s search=%s first=%s max=%s", realm.getId(), search, first, max);

    Groups groups = getGroups(realm.getId());

    return ids.map(groups::getGroupById)
        .filter(
            group ->
                search == null
                    || search.isEmpty()
                    || group.getName().toLowerCase().contains(search.toLowerCase()))
        .skip(first == null || first < 0 ? 0 : first)
        .limit(max == null || max < 0 ? Long.MAX_VALUE : max)
        .map(entityToAdapterFunc(realm));
  }

  @Override
  public Long getGroupsCount(RealmModel realm, Boolean onlyTopGroups) {
    log.debugf(
        "getGroupsCount: realmId=%s onlyTopGroups=%s",
        realm.getId(), Boolean.TRUE.equals(onlyTopGroups) ? "true" : "false");

    Groups groups = getGroups(realm.getId());

    if (Boolean.TRUE.equals(onlyTopGroups)) {
      return groups.getRealmGroups().stream().filter(group -> group.getParentId() == null).count();
    } else {
      return groups.getRealmGroups().stream().count();
    }
  }

  @Override
  public Long getGroupsCountByNameContaining(RealmModel realm, String search) {
    log.debugf("getGroupsCountByNameContaining: realmId=%s search=%s", realm.getId(), search);
    return searchForGroupByNameStream(realm, search, false, null, null).count();
  }

  @Override
  public Stream<GroupModel> getGroupsByRoleStream(
      RealmModel realm, RoleModel role, Integer firstResult, Integer maxResults) {
    log.debugf(
        "getGroupsByRoleStream: realmId=%s roleId=%s firstResult=%d maxResults=%d",
        realm.getId(), role.getId(), firstResult, maxResults);

    Groups groups = getGroups(realm.getId());
    return groups.getRealmGroups().stream()
        .filter(groupValue -> groupValue.getGrantedRoles().contains(role.getId()))
        .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
        .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
        .map(entityToAdapterFunc(realm));
  }

  @Override
  public Stream<GroupModel> getTopLevelGroupsStream(RealmModel realm) {
    return getTopLevelGroupsStream(realm, 0, -1);
  }

  @Override
  public Stream<GroupModel> getTopLevelGroupsStream(
      RealmModel realm, Integer firstResult, Integer maxResults) {
    Groups groups = getGroups(realm.getId());

    return groups.getRealmGroups().stream()
        .filter(group -> group.getParentId() == null)
        .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
        .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
        .map(entityToAdapterFunc(realm));
  }

  @Override
  public Stream<GroupModel> getTopLevelGroupsStream(
      RealmModel realm, String search, Boolean exact, Integer firstResult, Integer maxResults) {
    Groups groups = getGroups(realm.getId());

    return groups.getRealmGroups().stream()
        .filter(group -> group.getParentId() == null)
        .filter(
            group ->
                group.getName().equals(search)
                    || !exact && group.getName().toLowerCase().contains(search))
        .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
        .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
        .map(entityToAdapterFunc(realm));
  }

  private boolean groupExists(String realmId, String name, GroupModel parent) {
    Groups groups = getGroups(realmId);

    GroupValue groupValue =
        groups.getRealmGroups().stream()
            .filter(group -> group.getName().equals(name))
            .filter(
                subGroup ->
                    Objects.equals(subGroup.getParentId(), parent == null ? null : parent.getId()))
            .findFirst()
            .orElse(null);
    return (groupValue != null);
  }

  @Override
  public boolean removeGroup(RealmModel realm, GroupModel group) {
    log.debugf("removeGroup groupId=%s", group == null ? "null" : group.getId());

    if (group == null) {
      return false;
    } else {
      session.invalidate(GROUP_BEFORE_REMOVE, realm, group);

      Groups groups = getGroups(realm.getId());
      boolean removed = groups.removeRealmGroup(group.getId());
      if (removed) {
        markChanged(realm.getId());
      }

      session.invalidate(GROUP_AFTER_REMOVE, realm, group);
      return removed;
    }
  }

  @Override
  public void moveGroup(RealmModel realm, GroupModel group, GroupModel toParent) {
    log.debugf("moveGroup realm=%s group=%s toParent=%s, ", realm, group, toParent);

    GroupModel previousParent = group.getParent();

    if (toParent != null && group.getId().equals(toParent.getId())) {
      return;
    }

    if (groupExists(realm.getId(), group.getName(), toParent)) {
      throw new ModelDuplicateException(
          "Group with the same name or parent id exists:"
              + group.getName()
              + " for parent id "
              + (toParent == null ? "null" : toParent.getId()));
    }

    if (group.getParentId() != null) {
      group.getParent().removeChild(group);
    }
    group.setParent(toParent);
    if (toParent != null) toParent.addChild(group);

    String newPath = KeycloakModelUtils.buildGroupPath(group);
    String previousPath = KeycloakModelUtils.buildGroupPath(group, previousParent);

    GroupModel.GroupPathChangeEvent event =
        new GroupModel.GroupPathChangeEvent() {
          @Override
          public RealmModel getRealm() {
            return realm;
          }

          @Override
          public String getNewPath() {
            return newPath;
          }

          @Override
          public String getPreviousPath() {
            return previousPath;
          }

          @Override
          public KeycloakSession getKeycloakSession() {
            return session;
          }
        };
    session.getKeycloakSessionFactory().publish(event);
  }

  @Override
  public void addTopLevelGroup(RealmModel realm, GroupModel subGroup) {
    Groups groups = getGroups(realm.getId());
    GroupValue groupToUpdate = groups.getGroupById(subGroup.getId());
    if (groups.removeRealmGroup(groupToUpdate.getId())) {
      groupToUpdate.setParentId(null);
      groups.addRealmGroup(groupToUpdate);
      markChanged(realm.getId());
    }
  }

  @Override
  public GroupModel getGroupById(RealmModel realm, String id) {
    log.debugf("getGroupById realmId=%s id=%s", realm.getId(), id);
    Groups groups = getGroups(realm.getId());
    GroupValue group = groups.getGroupById(id);

    if (group == null) {
      return null;
    }

    return entityToAdapterFunc(realm).apply(group);
  }

  @Override
  public Stream<GroupModel> searchGroupsByAttributes(
      RealmModel realm, Map<String, String> attributes, Integer firstResult, Integer maxResults) {
    log.debugf(
        "searchGroupsByAttributes realmId=%s attributes=%s first=%d max=%d",
        realm.getId(), attributes, firstResult, maxResults);

    Groups groups = getGroups(realm.getId());

    return groups.getRealmGroups().stream()
        .filter(
            groupValue -> {
              for (Map.Entry<String, String> entry : attributes.entrySet()) {
                if (groupValue.getAttribute(entry.getKey()).contains(entry.getValue())) {
                  return true;
                }
              }
              return false;
            })
        .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
        .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
        .map(entityToAdapterFunc(realm));
  }

  @Override
  public Stream<GroupModel> searchForGroupByNameStream(
      RealmModel realm, String search, Boolean exact, Integer firstResult, Integer maxResults) {
    log.debugf(
        "searchForGroupByNameStream: realmId=%s search=%s exact=%s first=%d max=%d",
        realm.getId(),
        search,
        Boolean.TRUE.equals(exact) ? "true" : "false",
        firstResult,
        maxResults);

    Groups groups = getGroups(realm.getId());
    Stream<GroupValue> groupValueStream = groups.getRealmGroups().stream();

    if (Boolean.TRUE.equals(exact)) {
      groupValueStream = groupValueStream.filter(group -> group.getName().equals(search));
    } else {
      groupValueStream =
          groupValueStream.filter(
              group -> group.getName().toLowerCase().contains(search.toLowerCase()));
    }

    return groupValueStream
        .map(GroupValue::getId)
        .map(
            id -> {
              GroupValue groupById = groups.getGroupById(id);
              while (Objects.nonNull(groupById.getParentId())) {
                groupById = groups.getGroupById(groupById.getParentId());
              }
              return groupById;
            })
        .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
        .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
        .map(entityToAdapterFunc(realm))
        .sorted(GroupModel.COMPARE_BY_NAME)
        .distinct();
  }

  public void removeGroups(RealmModel realm) {
    log.debugf("removeGroups realmId=%s", realm.getId());

    groupRepository.deleteRealmGroups(realm.getId());
    markDeleted(realm.getId());
  }

  @Override
  public void close() {
    groupsByRealmId.clear();
    groupsChanged.clear();
    groupsDeleted.clear();
  }

  public void preRemove(RealmModel realm) {
    removeGroups(realm);
  }

  public void preRemove(RealmModel realm, RoleModel role) {
    getGroupsStream(realm).forEach(group -> group.deleteRoleMapping(role));
    markChanged(realm.getId());
  }
}
