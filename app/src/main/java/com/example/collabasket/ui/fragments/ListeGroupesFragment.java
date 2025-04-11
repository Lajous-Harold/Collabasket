package com.example.collabasket.ui.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.ProduitGroupes;
import com.example.collabasket.ui.adapter.ProduitGroupesAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.List;

public class ListeGroupesFragment extends Fragment {

    private String groupId, groupName;
    private FirebaseFirestore firestore;
    private ProduitGroupesAdapter produitAdapter;
    private RecyclerView recyclerView;

    private final String[] unitesDisponibles = new String[] { "pcs", "g", "kg", "ml", "L" };
    private final String[] categoriesDisponibles = new String[] {
            "Fruits et légumes", "Viandes et poissons", "Produits laitiers", "Boulangerie",
            "Épicerie sucrée", "Épicerie salée", "Boissons", "Surgelés",
            "Produits ménagers", "Hygiène et beauté", "Bébé", "Animaux",
            "Papeterie", "Textile", "Électronique", "Autre"
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_liste_groupes, container, false);

        if (getArguments() != null) {
            groupId = getArguments().getString("groupId");
            groupName = getArguments().getString("groupName");
        }

        TextView titre = rootView.findViewById(R.id.text_group_title);
        titre.setText("Liste du groupe : " + groupName);

        recyclerView = rootView.findViewById(R.id.recycler_produits);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        produitAdapter = new ProduitGroupesAdapter(groupId);
        recyclerView.setAdapter(produitAdapter);

        firestore = FirebaseFirestore.getInstance();
        loadProduitsForGroup();

        FloatingActionButton fab = rootView.findViewById(R.id.fab_ajouter_groupe);
        fab.setOnClickListener(v -> showAddProduitDialog());

        return rootView;
    }

    private void loadProduitsForGroup() {
        firestore.collection("groups")
                .document(groupId)
                .collection("produits")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ProduitGroupesAdapter.ProduitAvecId> produits = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ProduitGroupes produit = doc.toObject(ProduitGroupes.class);
                        produits.add(new ProduitGroupesAdapter.ProduitAvecId(doc.getId(), produit));
                    }
                    produitAdapter.setProduits(produits);
                });
    }

    private void showAddProduitDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_ajout_produit_groupes, null);

        EditText editNom = dialogView.findViewById(R.id.edit_nom);
        EditText editQuantite = dialogView.findViewById(R.id.edit_quantite);
        Spinner spinnerUnite = dialogView.findViewById(R.id.spinner_unite);
        Spinner spinnerCategorie = dialogView.findViewById(R.id.spinner_categorie);

        ArrayAdapter<String> uniteAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, unitesDisponibles);
        uniteAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUnite.setAdapter(uniteAdapter);

        ArrayAdapter<String> categorieAdapter = new ArrayAdapter<>(getContext(),
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

                    float quantite;
                    try {
                        quantite = Float.parseFloat(qteText);
                    } catch (NumberFormatException e) {
                        quantite = 1f;
                    }

                    if (!nom.isEmpty()) {
                        float finalQuantite = quantite; // ✅ capturé ici
                        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .get()
                                .addOnSuccessListener(documentSnapshot -> {
                                    String ajoutePar = documentSnapshot.getString("username");
                                    if (ajoutePar == null || ajoutePar.isEmpty()) {
                                        ajoutePar = "Inconnu";
                                    }

                                    ProduitGroupes produit = new ProduitGroupes(nom, categorie, finalQuantite, unite, groupId, ajoutePar);

                                    firestore.collection("groups")
                                            .document(groupId)
                                            .collection("produits")
                                            .add(produit)
                                            .addOnSuccessListener(ref -> loadProduitsForGroup());
                                });
                    } else {
                        Toast.makeText(getContext(), "Le nom du produit est requis", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }
}