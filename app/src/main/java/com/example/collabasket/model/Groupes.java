package com.example.collabasket.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Groupes {

    private String id;  // Nécessaire pour DiffUtil
    private String groupName;
    private String ownerId;
    private Map<String, Map<String, String>> members;
    private List<String> memberIds;

    public Groupes() {
        // Nécessaire pour Firestore
    }

    public Groupes(String groupName, String ownerId, Map<String, Map<String, String>> members, List<String> memberIds) {
        this.groupName = groupName;
        this.ownerId = ownerId;
        this.members = members;
        this.memberIds = memberIds;
    }

    // Getter et Setter pour l'ID Firestore
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Map<String, Map<String, String>> getMembers() {
        return members;
    }

    public void setMembers(Map<String, Map<String, String>> members) {
        this.members = members;
    }
    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Groupes)) return false;
        Groupes groupes = (Groupes) o;
        return Objects.equals(id, groupes.id) &&
                Objects.equals(groupName, groupes.groupName) &&
                Objects.equals(ownerId, groupes.ownerId) &&
                Objects.equals(members, groupes.members) &&
                Objects.equals(memberIds, groupes.memberIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, groupName, ownerId, members, memberIds);
    }
}
