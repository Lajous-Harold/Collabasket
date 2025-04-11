package com.example.collabasket.model;

import androidx.annotation.NonNull;

public class Produit {

    public String nom;
    public String categorie;
    public float quantite;
    public String unite;
    public boolean coche = false;

    public String userId; // UID Firebase

    public Produit() {} // Obligatoire pour Firebase

    public Produit(@NonNull String nom, String categorie, float quantite, String unite, String userId) {
        this.nom = nom;
        this.categorie = categorie;
        this.quantite = quantite;
        this.unite = unite;
        this.userId = userId;
        this.coche = false;
    }
}
