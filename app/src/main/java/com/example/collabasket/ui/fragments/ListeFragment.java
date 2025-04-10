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
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ListeFragment extends Fragment {

    private ProduitAdapter adapter;
    private FirebaseFirestore firestore;
    private CollectionReference produitsRef;

    private final String[] unitesDisponibles = new String[] {
            "pcs", "g", "kg", "ml", "L"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_liste, container, false);

        RecyclerView recyclerView = rootView.findViewById(R.id.recycler_view_produits);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        adapter = new ProduitAdapter();
        recyclerView.setAdapter(adapter);

        firestore = FirebaseFirestore.getInstance();
        produitsRef = firestore.collection("produits");

        produitsRef.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e("FIRESTORE", "Erreur d'écoute : ", error);
                return;
            }

            List<Produit> produits = new ArrayList<>();
            if (value != null) {
                for (QueryDocumentSnapshot doc : value) {
                    produits.add(doc.toObject(Produit.class));
                }
                adapter.setProduits(produits);
            }
        });

        FloatingActionButton fab = rootView.findViewById(R.id.fab_ajouter);
        fab.setOnClickListener(v -> {
            View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_ajout_produit, null);
            EditText editNom = dialogView.findViewById(R.id.edit_nom);
            EditText editCategorie = dialogView.findViewById(R.id.edit_categorie);
            EditText editQuantite = dialogView.findViewById(R.id.edit_quantite);
            Spinner spinnerUnite = dialogView.findViewById(R.id.spinner_unite);

            // Remplir le Spinner avec les unités
            ArrayAdapter<String> adapterSpinner = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, unitesDisponibles);
            adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerUnite.setAdapter(adapterSpinner);

            new AlertDialog.Builder(getContext())
                    .setTitle("Ajouter un produit")
                    .setView(dialogView)
                    .setPositiveButton("Ajouter", (dialog, which) -> {
                        String nom = editNom.getText().toString().trim();
                        String categorie = editCategorie.getText().toString().trim();
                        String qteText = editQuantite.getText().toString().trim();
                        String unite = spinnerUnite.getSelectedItem().toString();

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
