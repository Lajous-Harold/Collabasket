package com.example.collabasket.model;

import com.google.firebase.Timestamp;

public class ProduitHistorique {
    public String nom;
    public String categorie;
    public float quantite;
    public String unite;
    public Timestamp dateAjout;

    public ProduitHistorique() {}

    public ProduitHistorique(Produit produit) {
        this.nom = produit.nom;
        this.categorie = produit.categorie;
        this.quantite = produit.quantite;
        this.unite = produit.unite;
        this.dateAjout = Timestamp.now();
    }
}
