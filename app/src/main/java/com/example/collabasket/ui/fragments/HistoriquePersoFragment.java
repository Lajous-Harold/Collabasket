package com.example.collabasket.ui.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.ProduitHistorique;
import com.example.collabasket.utils.HistoriqueLoader;
import com.example.collabasket.ui.adapter.ProduitHistoriqueAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class HistoriquePersoFragment extends Fragment {

    private SearchView searchView;
    private Spinner spinnerTri;
    private Spinner spinnerCategorie;
    private RecyclerView recyclerView;
    private ProduitHistoriqueAdapter adapter;

    private final String[] criteresTri = {"date", "nom", "categorie"};
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
        View view = inflater.inflate(R.layout.fragment_historique, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_historique, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_vider_historique) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Confirmation")
                            .setMessage("Voulez-vous vraiment supprimer tout l'historique ?")
                            .setPositiveButton("Oui", (dialog, which) -> viderHistorique())
                            .setNegativeButton("Annuler", null)
                            .show();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner());

        searchView = view.findViewById(R.id.search_view);
        spinnerTri = view.findViewById(R.id.spinner_tri);
        spinnerCategorie = view.findViewById(R.id.spinner_categorie);
        recyclerView = view.findViewById(R.id.recycler_historique);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ProduitHistoriqueAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        ArrayAdapter<String> triAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, criteresTri);
        triAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTri.setAdapter(triAdapter);

        ArrayAdapter<String> categorieAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, categoriesDisponibles);
        categorieAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategorie.setAdapter(categorieAdapter);

        Runnable chargerHistorique = this::chargerHistorique;

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                chargerHistorique.run();
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                chargerHistorique.run();
                return true;
            }
        });

        spinnerTri.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) { chargerHistorique.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        spinnerCategorie.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) { chargerHistorique.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        chargerHistorique.run();
        return view;
    }

    private void chargerHistorique() {
        String tri = spinnerTri.getSelectedItem().toString();
        String recherche = searchView.getQuery().toString().trim();
        String categorie = spinnerCategorie.getSelectedItem().toString();
        if ("Toutes".equals(categorie)) categorie = "";

        HistoriqueLoader.loadHistorique(tri, recherche, categorie, resultList -> {
            adapter.setProduits(resultList);
        }, () -> {
            Toast.makeText(getContext(), "Erreur lors du chargement de l'historique", Toast.LENGTH_SHORT).show();
        });
    }

    private void viderHistorique() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("historique")
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (var doc : snapshot.getDocuments()) {
                        doc.getReference().delete();
                    }
                    chargerHistorique();
                    Toast.makeText(getContext(), "Historique vidé", Toast.LENGTH_SHORT).show();
                });
    }
}