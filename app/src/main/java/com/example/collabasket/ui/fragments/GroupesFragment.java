package com.example.collabasket.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

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
        fabAddGroup = view.findViewById(R.id.fab_add_groupes);

        firestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        groupesAdapter = new GroupesAdapter((group, groupId) -> {
            // Lorsque l'utilisateur clique sur un groupe, on le redirige
            ListeGroupesFragment listeGroupesFragment = new ListeGroupesFragment();
            Bundle args = new Bundle();
            args.putString("groupId", groupId);
            args.putString("groupName", group.getGroupName());
            listeGroupesFragment.setArguments(args);
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, listeGroupesFragment)
                    .addToBackStack(null)
                    .commit();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(groupesAdapter);

        loadUserGroups();

        fabAddGroup.setOnClickListener(v -> showCreateGroupDialog());

        return view;
    }

    private void loadUserGroups() {
        String userId = mAuth.getCurrentUser().getUid();

        firestore.collection("groups")
                .whereArrayContains("memberIds", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Groupes> userGroups = new ArrayList<>();
                    List<String> groupIds = new ArrayList<>();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Groupes group = document.toObject(Groupes.class);
                        userGroups.add(group);
                        groupIds.add(document.getId());
                    }

                    groupesAdapter.setGroups(userGroups, groupIds);

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
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_groupes, null);
        EditText groupNameInput = dialogView.findViewById(R.id.text_group_title);

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Créer un groupe")
                .setView(dialogView)
                .setPositiveButton("Créer", (dialog, which) -> {
                    String groupName = groupNameInput.getText().toString().trim();

                    if (!groupName.isEmpty()) {
                        String userId = mAuth.getCurrentUser().getUid();
                        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

                        firestore.collection("users")
                                .document(userId)
                                .get()
                                .addOnSuccessListener(documentSnapshot -> {
                                    // Construction des membres
                                    List<Map<String, String>> members = new ArrayList<>();
                                    Map<String, String> user = new HashMap<>();
                                    user.put("userId", userId);
                                    user.put("userName", documentSnapshot.getString("username"));
                                    user.put("numero", documentSnapshot.getString("phone"));
                                    user.put("role", "Propriétaire");

                                    members.add(user);

                                    List<String> memberIds = new ArrayList<>();
                                    memberIds.add(userId);

                                    Groupes newGroup = new Groupes(groupName, userId, members, memberIds);

                                    firestore.collection("groups")
                                            .add(newGroup)
                                            .addOnSuccessListener(documentReference -> {
                                                Toast.makeText(getContext(), "Groupe créé avec succès", Toast.LENGTH_SHORT).show();
                                                loadUserGroups();
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(getContext(), "Erreur lors de la création du groupe", Toast.LENGTH_SHORT).show();
                                            });
                                });
                    } else {
                        Toast.makeText(getContext(), "Le nom du groupe ne peut pas être vide", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }
}
