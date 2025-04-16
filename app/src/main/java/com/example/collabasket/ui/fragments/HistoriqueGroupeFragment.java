package com.example.collabasket.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.model.ProduitGroupesHistorique;
import com.example.collabasket.ui.adapter.ProduitGroupesHistoriqueAdapter;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class HistoriqueGroupeFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProduitGroupesHistoriqueAdapter adapter;
    private FirebaseFirestore firestore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_historique_groupe, container, false);

        Toolbar toolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());

        recyclerView = view.findViewById(R.id.recycler_historique_groupe);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ProduitGroupesHistoriqueAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        firestore = FirebaseFirestore.getInstance();

        chargerHistoriqueGroupe();

        return view;
    }

    private void chargerHistoriqueGroupe() {
        String groupId = getArguments() != null ? getArguments().getString("groupId") : "";

        firestore.collection("groups")
                .document(groupId)
                .collection("historique")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ProduitGroupesHistorique> produits = snapshot.toObjects(ProduitGroupesHistorique.class);
                    adapter.setProduits(produits);
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Erreur de chargement", Toast.LENGTH_SHORT).show());
    }
}