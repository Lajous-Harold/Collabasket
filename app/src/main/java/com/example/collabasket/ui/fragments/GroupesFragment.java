package com.example.collabasket.ui.fragments;


import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.ui.adapter.GroupesAdapter;
import com.example.collabasket.model.Groupes;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class GroupesFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView textEmptyGroups;
    private FloatingActionButton fabAddGroup;
    private GroupesAdapter groupesAdapter;
    private FirebaseFirestore firestore;
    private FirebaseAuth mAuth;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_groupes, container, false);

        recyclerView = view.findViewById(R.id.recycler_view_groups);
        textEmptyGroups = view.findViewById(R.id.text_empty_groups);
        fabAddGroup = view.findViewById(R.id.fab_add_group);

        firestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        groupesAdapter = new GroupesAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(groupesAdapter);

        loadUserGroups();

        fabAddGroup.setOnClickListener(v -> showCreateGroupDialog());

        return view;
    }

    private void loadUserGroups() {
        String userId = mAuth.getCurrentUser().getUid();

        firestore.collection("groups")
                .whereArrayContains("members", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Groupes> userGroups = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Groupes group = document.toObject(Groupes.class);
                        userGroups.add(group);
                    }
                    groupesAdapter.setGroups(userGroups);

                    if (userGroups.isEmpty()) {
                        textEmptyGroups.setVisibility(View.VISIBLE);
                    } else {
                        textEmptyGroups.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("GroupesFragment", "Erreur lors du chargement des groupes", e);
                    Toast.makeText(getContext(), "Erreur lors du chargement des groupes", Toast.LENGTH_SHORT).show();
                });
    }

    private void showCreateGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Créer un groupe");

        final EditText input = new EditText(requireContext());
        input.setHint("Nom du groupe");
        builder.setView(input);

        builder.setPositiveButton("Créer", (dialog, which) -> {
            String groupName = input.getText().toString().trim();
            if (!groupName.isEmpty()) {
                createGroup(groupName);
            } else {
                Toast.makeText(getContext(), "Nom de groupe vide", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Annuler", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void createGroup(String name) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Création du membre avec rôle
        Map<String, String> membre = new HashMap<>();
        membre.put("uid", userId);
        membre.put("role", "admin"); // ou "creator", "owner", comme tu veux

        List<Map<String, String>> members = new ArrayList<>();
        members.add(membre);

        // Données du groupe à envoyer à Firestore
        Map<String, Object> groupData = new HashMap<>();
        groupData.put("groupName", name);
        groupData.put("creatorUid", userId);
        groupData.put("members", members);

        FirebaseFirestore.getInstance().collection("groups")
                .add(groupData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(getContext(), "Groupe créé", Toast.LENGTH_SHORT).show();
                    loadUserGroups(); // Recharge la liste
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Erreur lors de la création", Toast.LENGTH_SHORT).show();
                    Log.e("GroupesFragment", "Erreur création groupe", e);
                });
    }

}
