package com.example.collabasket.model;

import java.util.List;
import java.util.Map;

public class Groupes {

    private String groupName;
    private String creatorUid;
    private List<Map<String, String>> members;  // Liste des membres avec rôle

    public Groupes() {
        // Constructeur par défaut requis pour Firebase
    }

    public Groupes(String groupName, String creatorUid, List<Map<String, String>> members) {
        this.groupName = groupName;
        this.creatorUid = creatorUid;
        this.members = members;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getCreatorUid() {
        return creatorUid;
    }

    public void setCreatorUid(String creatorUid) {
        this.creatorUid = creatorUid;
    }

    public List<Map<String, String>> getMembers() {
        return members;
    }

    public void setMembers(List<Map<String, String>> members) {
        this.members = members;
    }
}
