package com.example.collabasket.model;

public class Produit {

    public String nom;
    public String categorie;
    public float quantite;
    public String unite;
    public boolean coche;

    public Produit() {}

    public Produit(String nom, String categorie, float quantite, String unite) {
        this.nom = nom;
        this.categorie = categorie;
        this.quantite = quantite;
        this.unite = unite;
        this.coche = false;
    }
}
