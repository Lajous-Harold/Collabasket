package com.example.collabasket.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.collabasket.R;
import com.example.collabasket.ui.adapter.GroupesAdapter;
import com.example.collabasket.model.Groupes;
import com.example.collabasket.ListeGroupesActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

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

        groupesAdapter = new GroupesAdapter((group, groupId) -> {
            Intent intent = new Intent(getContext(), ListeGroupesActivity.class);
            intent.putExtra("groupId", groupId);
            intent.putExtra("groupName", group.getGroupName());
            startActivity(intent);
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
        // (code de création du groupe non répété ici pour clarté)
    }
}
