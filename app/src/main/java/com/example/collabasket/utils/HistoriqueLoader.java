package com.example.collabasket.utils;

import android.util.Log;

import com.example.collabasket.model.ProduitGroupesHistorique;
import com.example.collabasket.model.ProduitHistorique;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HistoriqueLoader {

    public static void loadHistoriquePerso(String critere, String recherche, String filtreCategorie,
                                           Consumer<List<ProduitHistorique>> onSuccess, Runnable onFailure) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        Query query = firestore.collection("users")
                .document(uid)
                .collection("historique");

        switch (critere) {
            case "categorie":
                query = query.orderBy("categorie");
                break;
            case "nom":
                query = query.orderBy("nom");
                break;
            default:
                query = query.orderBy("dateAjout", Query.Direction.DESCENDING);
                break;
        }

        query.get().addOnSuccessListener(snapshot -> {
            List<ProduitHistorique> result = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshot) {
                ProduitHistorique p = doc.toObject(ProduitHistorique.class);
                boolean matchNom = (recherche == null || recherche.isEmpty() ||
                        p.nom.toLowerCase().contains(recherche.toLowerCase()));
                boolean matchCat = (filtreCategorie == null || filtreCategorie.isEmpty() ||
                        p.categorie.equalsIgnoreCase(filtreCategorie));
                if (matchNom && matchCat) result.add(p);
            }
            onSuccess.accept(result);
        }).addOnFailureListener(e -> {
            Log.e("HistoriqueLoader", "Erreur chargement perso", e);
            onFailure.run();
        });
    }

    public static void loadHistoriqueGroupe(String groupId, String critere, String recherche, String filtreCategorie,
                                            Consumer<List<ProduitGroupesHistorique>> onSuccess, Runnable onFailure) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        Query query = firestore.collection("groups")
                .document(groupId)
                .collection("historique");

        query.get().addOnSuccessListener(snapshot -> {
            List<ProduitGroupesHistorique> result = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snapshot) {
                ProduitGroupesHistorique p = doc.toObject(ProduitGroupesHistorique.class);
                boolean matchNom = (recherche == null || recherche.isEmpty() ||
                        p.nom.toLowerCase().contains(recherche.toLowerCase()));
                boolean matchAjoutePar = (recherche == null || recherche.isEmpty() ||
                        p.ajoutePar.toLowerCase().contains(recherche.toLowerCase()));
                boolean matchCat = (filtreCategorie == null || filtreCategorie.isEmpty() ||
                        p.categorie.equalsIgnoreCase(filtreCategorie));

                if ((matchNom || matchAjoutePar) && matchCat) result.add(p);
            }

            switch (critere) {
                case "nom":
                    result.sort((a, b) -> a.nom.compareToIgnoreCase(b.nom));
                    break;
                case "categorie":
                    result.sort((a, b) -> a.categorie.compareToIgnoreCase(b.categorie));
                    break;
                case "ajouté par":
                    result.sort((a, b) -> a.ajoutePar.compareToIgnoreCase(b.ajoutePar));
                    break;
                case "date":
                default:
                    // si champ "dateAjout" existait, on trierait ici
                    break;
            }
            onSuccess.accept(result);
        }).addOnFailureListener(e -> {
            Log.e("HistoriqueLoader", "Erreur chargement groupe", e);
            onFailure.run();
        });
    }
}