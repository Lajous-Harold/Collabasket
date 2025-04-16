package com.example.collabasket.utils;

import android.util.Log;

import com.example.collabasket.model.ProduitHistorique;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HistoriqueLoader {

    public static void loadHistorique(String critere, String recherche, String filtreCategorie, Consumer<List<ProduitHistorique>> onSuccess, Runnable onFailure) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        Query query = firestore.collection("users")
                .document(uid)
                .collection("historique");

        switch (critere) {
            case "categorie":
                query = query.orderBy("categorie", Query.Direction.ASCENDING);
                break;
            case "nom":
                query = query.orderBy("nom", Query.Direction.ASCENDING);
                break;
            default:
                query = query.orderBy("dateAjout", Query.Direction.DESCENDING);
                break;
        }

        query.get().addOnSuccessListener(snapshot -> {
            List<ProduitHistorique> historiques = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshot) {
                ProduitHistorique p = doc.toObject(ProduitHistorique.class);
                boolean correspondNom = (recherche == null || recherche.isEmpty() ||
                        p.nom.toLowerCase().contains(recherche.toLowerCase()));
                boolean correspondCategorie = (filtreCategorie == null || filtreCategorie.isEmpty() ||
                        p.categorie.equalsIgnoreCase(filtreCategorie));

                if (correspondNom && correspondCategorie) {
                    historiques.add(p);
                }
            }
            onSuccess.accept(historiques);
        }).addOnFailureListener(e -> {
            Log.e("HistoriqueLoader", "Erreur chargement historique : ", e);
            onFailure.run();
        });
    }
}