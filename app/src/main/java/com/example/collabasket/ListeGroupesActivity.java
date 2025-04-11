package com.example.collabasket;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import com.example.collabasket.model.ProduitGroupes;
import com.example.collabasket.ui.adapter.ProduitGroupesAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ListeGroupesActivity extends AppCompatActivity {

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
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_liste_groupes);

        groupId = getIntent().getStringExtra("groupId");
        groupName = getIntent().getStringExtra("groupName");

        TextView titre = findViewById(R.id.text_group_title);
        titre.setText("Liste du groupe : " + groupName);

        recyclerView = findViewById(R.id.recycler_produits);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        produitAdapter = new ProduitGroupesAdapter();
        recyclerView.setAdapter(produitAdapter);

        firestore = FirebaseFirestore.getInstance();
        loadProduitsForGroup();

        FloatingActionButton fab = findViewById(R.id.fab_ajouter_produit);
        fab.setOnClickListener(v -> showAddProduitDialog());
    }

    private void loadProduitsForGroup() {
        firestore.collection("elements")
                .whereEqualTo("groupId", groupId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<ProduitGroupes> produits = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        ProduitGroupes produit = doc.toObject(ProduitGroupes.class);
                        produits.add(produit);
                    }
                    produitAdapter.setProduits(produits);
                })
                .addOnFailureListener(e -> {
                    Log.e("ListeGroupesActivity", "Erreur de chargement des produits", e);
                    Toast.makeText(this, "Erreur lors du chargement", Toast.LENGTH_SHORT).show();
                });
    }

    private void showAddProduitDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_ajout_produit_groupes, null);

        EditText editNom = dialogView.findViewById(R.id.edit_nom);
        EditText editQuantite = dialogView.findViewById(R.id.edit_quantite);
        Spinner spinnerUnite = dialogView.findViewById(R.id.spinner_unite);
        Spinner spinnerCategorie = dialogView.findViewById(R.id.spinner_categorie);

        ArrayAdapter<String> uniteAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, unitesDisponibles);
        uniteAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUnite.setAdapter(uniteAdapter);

        ArrayAdapter<String> categorieAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categoriesDisponibles);
        categorieAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategorie.setAdapter(categorieAdapter);

        new AlertDialog.Builder(this)
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
                        String ajoutePar = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
                        if (ajoutePar == null || ajoutePar.isEmpty()) {
                            ajoutePar = "Inconnu";
                        }

                        ProduitGroupes produit = new ProduitGroupes(nom, categorie, quantite, unite, groupId, ajoutePar);

                        firestore.collection("elements")
                                .add(produit)
                                .addOnSuccessListener(documentReference -> {
                                    Toast.makeText(this, "Produit ajouté", Toast.LENGTH_SHORT).show();
                                    loadProduitsForGroup();
                                    View rootView = findViewById(android.R.id.content);
                                    Snackbar.make(rootView, "Produit ajouté", Snackbar.LENGTH_LONG)
                                            .setAction("Annuler", v2 -> {
                                                documentReference.delete()
                                                        .addOnSuccessListener(aVoid ->
                                                                Toast.makeText(this, "Ajout annulé", Toast.LENGTH_SHORT).show())
                                                        .addOnFailureListener(e ->
                                                                Toast.makeText(this, "Erreur lors de l’annulation", Toast.LENGTH_SHORT).show());
                                            })
                                            .show();
                                })
                                .addOnFailureListener(e -> {
                                    Toast.makeText(this, "Erreur lors de l’ajout", Toast.LENGTH_SHORT).show();
                                    Log.e("ListeGroupesActivity", "Erreur ajout produit", e);
                                });

                    } else {
                        Toast.makeText(this, "Nom requis", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }
}
