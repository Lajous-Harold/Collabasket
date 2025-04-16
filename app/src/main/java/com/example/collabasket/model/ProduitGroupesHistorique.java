package com.example.collabasket.model;

import com.google.firebase.Timestamp;

public class ProduitGroupesHistorique {

    public String nom;
    public String categorie;
    public float quantite;
    public String unite;
    public String ajoutePar;
    public Timestamp dateAjout;

    public ProduitGroupesHistorique() {} // requis par Firestore

    public ProduitGroupesHistorique(ProduitGroupes produit) {
        this.nom = produit.nom;
        this.categorie = produit.categorie;
        this.quantite = produit.quantite;
        this.unite = produit.unite;
        this.ajoutePar = produit.ajoutePar;
        this.dateAjout = Timestamp.now();
    }
}
