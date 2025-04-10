package com.example.collabasket.model;

public class Produit {

    public String nom;
    public String categorie;
    public int quantite;
    public boolean coche;

    public Produit() {}

    public Produit(String nom, String categorie, int quantite) {
        this.nom = nom;
        this.categorie = categorie;
        this.quantite = quantite;
        this.coche = false;
    }
}
