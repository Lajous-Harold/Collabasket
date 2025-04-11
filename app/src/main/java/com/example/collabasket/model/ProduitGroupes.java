package com.example.collabasket.model;

public class ProduitGroupes {

    private String nom;
    private String categorie;
    private float quantite;
    private String unite;
    private String groupId;
    private String ajoutePar;

    public ProduitGroupes() {
        // Constructeur requis pour Firestore
    }

    public ProduitGroupes(String nom, String categorie, float quantite, String unite, String groupId, String ajoutePar) {
        this.nom = nom;
        this.categorie = categorie;
        this.quantite = quantite;
        this.unite = unite;
        this.groupId = groupId;
        this.ajoutePar = ajoutePar;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getCategorie() {
        return categorie;
    }

    public void setCategorie(String categorie) {
        this.categorie = categorie;
    }

    public float getQuantite() {
        return quantite;
    }

    public void setQuantite(float quantite) {
        this.quantite = quantite;
    }

    public String getUnite() {
        return unite;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getAjoutePar() {
        return ajoutePar;
    }

    public void setAjoutePar(String ajoutePar) {
        this.ajoutePar = ajoutePar;
    }
}
