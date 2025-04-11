package com.example.collabasket.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Groupes {

    private String groupName;
    private String creatorUid;
    private List<String> memberIds;
    private List<Map<String, String>> members;
    private List<ProduitGroupes> produits = new ArrayList<>();

    public Groupes() {}

    public Groupes(String groupName, String creatorUid, List<Map<String, String>> members, List<String> memberIds) {
        this.groupName = groupName;
        this.creatorUid = creatorUid;
        this.members = members;
        this.memberIds = memberIds;
        this.produits = new ArrayList<>();
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

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
    }

    public List<Map<String, String>> getMembers() {
        return members;
    }

    public void setMembers(List<Map<String, String>> members) {
        this.members = members;
    }

    public List<ProduitGroupes> getProduits() {
        return produits;
    }

    public void setProduits(List<ProduitGroupes> produits) {
        this.produits = produits;
    }
}
