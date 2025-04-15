package com.example.collabasket.ui.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.Produit;
import com.example.collabasket.ui.adapter.ProduitAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.List;

public class ListeFragment extends Fragment {

    private ProduitAdapter adapter;
    private FirebaseFirestore firestore;
    private CollectionReference produitsRef;
    private TextView textEmptyList;
    private final String[] unitesDisponibles = { "pcs", "g", "kg", "ml", "L" };
    private final String[] categoriesDisponibles = {
            "Fruits et légumes", "Viandes et poissons", "Produits laitiers", "Boulangerie",
            "Épicerie sucrée", "Épicerie salée", "Boissons", "Surgelés",
            "Produits ménagers", "Hygiène et beauté", "Bébé", "Animaux",
            "Papeterie", "Textile", "Électronique", "Autre"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "ListeFragment onCreateView");

        View rootView = inflater.inflate(R.layout.fragment_liste, container, false);

        RecyclerView recyclerView = rootView.findViewById(R.id.recycler_view_produits);
        textEmptyList = rootView.findViewById(R.id.text_empty_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        adapter = new ProduitAdapter();
        recyclerView.setAdapter(adapter);

        firestore = FirebaseFirestore.getInstance();
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        produitsRef = firestore.collection("users")
                .document(currentUserId)
                .collection("produits");

        // 🔄 Synchronisation des produits personnels
        produitsRef.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e("FIRESTORE", "Erreur d'écoute Firestore : ", error);
                return;
            }

            List<Produit> produits = new ArrayList<>();
            if (value != null) {
                for (QueryDocumentSnapshot doc : value) {
                    produits.add(doc.toObject(Produit.class));
                }
                adapter.setProduits(produits);

                // Afficher ou masquer le message de liste vide
                if (textEmptyList != null) {
                    textEmptyList.setVisibility(produits.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
        });

        FloatingActionButton fab = rootView.findViewById(R.id.fab_ajouter);
        fab.setOnClickListener(v -> {
            View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_ajout_produit, null);
            EditText editNom = dialogView.findViewById(R.id.edit_nom);
            EditText editQuantite = dialogView.findViewById(R.id.edit_quantite);
            Spinner spinnerUnite = dialogView.findViewById(R.id.spinner_unite);
            Spinner spinnerCategorie = dialogView.findViewById(R.id.spinner_categorie);

            ArrayAdapter<String> uniteAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, unitesDisponibles);
            uniteAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerUnite.setAdapter(uniteAdapter);

            ArrayAdapter<String> categorieAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, categoriesDisponibles);
            categorieAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCategorie.setAdapter(categorieAdapter);

            new AlertDialog.Builder(getContext())
                    .setTitle("Ajouter un produit")
                    .setView(dialogView)
                    .setPositiveButton("Ajouter", (dialog, which) -> {
                        String nom = editNom.getText().toString().trim();
                        String qteText = editQuantite.getText().toString().trim();
                        String unite = spinnerUnite.getSelectedItem().toString();
                        String categorie = spinnerCategorie.getSelectedItem().toString();

                        float quantite = 1;
                        try {
                            quantite = Float.parseFloat(qteText);
                        } catch (NumberFormatException e) {
                            quantite = 1;
                        }

                        if (!nom.isEmpty()) {
                            Produit produit = new Produit(nom, categorie, quantite, unite);
                            produitsRef.add(produit);
                        }
                    })
                    .setNegativeButton("Annuler", null)
                    .show();
        });

        adapter.setOnSuppressionListener(produit -> {
            produitsRef
                    .whereEqualTo("nom", produit.nom)
                    .whereEqualTo("categorie", produit.categorie)
                    .whereEqualTo("quantite", produit.quantite)
                    .whereEqualTo("unite", produit.unite)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            doc.getReference().delete();
                        }
                    });
        });

        return rootView;
    }
}