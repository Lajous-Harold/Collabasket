package com.example.collabasket.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.ProduitGroupesHistorique;
import com.example.collabasket.ui.adapter.ProduitGroupesHistoriqueAdapter;
import com.example.collabasket.utils.HistoriqueLoader;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class HistoriqueGroupeFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProduitGroupesHistoriqueAdapter adapter;
    private FirebaseFirestore firestore;
    private String groupId;

    private SearchView searchView;
    private Spinner spinnerTri;
    private Spinner spinnerCategorie;

    private final String[] criteresTri = {"date", "nom", "categorie", "ajouté par"};
    private final String[] categoriesDisponibles = {
            "Toutes", "Fruits et légumes", "Viandes et poissons", "Produits laitiers", "Boulangerie",
            "Épicerie sucrée", "Épicerie salée", "Boissons", "Surgelés",
            "Produits ménagers", "Hygiène et beauté", "Bébé", "Animaux",
            "Papeterie", "Textile", "Électronique", "Autre"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_historique_groupe, container, false);

        groupId = getArguments() != null ? getArguments().getString("groupId") : null;
        if (groupId == null || groupId.isEmpty()) {
            Toast.makeText(getContext(), "Erreur : ID du groupe manquant", Toast.LENGTH_LONG).show();
            requireActivity().onBackPressed();
            return view;
        }

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        searchView = view.findViewById(R.id.search_view);
        spinnerTri = view.findViewById(R.id.spinner_tri);
        spinnerCategorie = view.findViewById(R.id.spinner_categorie);
        recyclerView = view.findViewById(R.id.recycler_historique_groupe);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new ProduitGroupesHistoriqueAdapter(new ArrayList<>());
        adapter.setGroupId(groupId);
        recyclerView.setAdapter(adapter);

        firestore = FirebaseFirestore.getInstance();

        ArrayAdapter<String> triAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, criteresTri);
        triAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTri.setAdapter(triAdapter);

        ArrayAdapter<String> categorieAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, categoriesDisponibles);
        categorieAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategorie.setAdapter(categorieAdapter);

        Runnable charger = this::chargerHistoriqueGroupe;

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { charger.run(); return true; }
            @Override public boolean onQueryTextChange(String newText) { charger.run(); return true; }
        });

        spinnerTri.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { charger.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        spinnerCategorie.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { charger.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        view.findViewById(R.id.btn_vider_historique).setOnClickListener(v -> {
            String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore.getInstance()
                    .collection("groups")
                    .document(groupId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        String role = snapshot.getString("members." + currentUid + ".role");
                        if ("Propriétaire".equals(role) || "Administrateur".equals(role)) {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Confirmation")
                                    .setMessage("Voulez-vous vraiment vider tout l’historique du groupe ?")
                                    .setPositiveButton("Oui", (dialog, which) -> viderHistorique())
                                    .setNegativeButton("Annuler", null)
                                    .show();
                        } else {
                            Toast.makeText(getContext(), "Permission refusée", Toast.LENGTH_SHORT).show();
                        }
                    });
        });


        charger.run();
        return view;
    }

    private void chargerHistoriqueGroupe() {
        String tri = spinnerTri.getSelectedItem().toString();
        String recherche = searchView.getQuery().toString().trim();
        String selectedCategorie = spinnerCategorie.getSelectedItem().toString();
        String categorie = "Toutes".equals(selectedCategorie) ? "" : selectedCategorie;

        HistoriqueLoader.loadHistoriqueGroupe(groupId, tri, recherche, categorie, resultList -> {
            adapter.setProduits(resultList);
        }, () -> {
            Toast.makeText(getContext(), "Erreur lors du chargement de l'historique", Toast.LENGTH_SHORT).show();
        });
    }

    private void viderHistorique() {
        firestore.collection("groups")
                .document(groupId)
                .collection("historique")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (var doc : querySnapshot.getDocuments()) {
                        doc.getReference().delete();
                    }
                    adapter.setProduits(new ArrayList<>());
                    Toast.makeText(getContext(), "Historique vidé", Toast.LENGTH_SHORT).show();
                });
    }
}
