package com.example.collabasket.utils;

import com.example.collabasket.model.Produit;
import com.example.collabasket.model.ProduitHistorique;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class HistoriqueUtils {

    public static void ajouterProduitSiAbsent(Produit produit) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        firestore.collection("users")
                .document(uid)
                .collection("historique")
                .whereEqualTo("nom", produit.nom)
                .whereEqualTo("categorie", produit.categorie)
                .whereEqualTo("quantite", produit.quantite)
                .whereEqualTo("unite", produit.unite)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        firestore.collection("users")
                                .document(uid)
                                .collection("historique")
                                .add(new ProduitHistorique(produit));
                    }
                });
    }
}
