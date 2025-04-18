package com.example.collabasket.ui.fragments;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.collabasket.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentReference;

import java.util.HashMap;
import java.util.Map;

public class InfoGroupeFragment extends Fragment {

    private String groupId;
    private String currentUserId;
    private String currentUserRole;
    private TextView nomGroupe;
    private LinearLayout listeMembres;
    private Button btnQuitter;
    private Button btnSupprimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d("DEBUG_STARTUP", "InfoGroupeFragment onCreateView");

        View view = inflater.inflate(R.layout.fragment_info_groupe, container, false);

        ImageButton btnRetour = view.findViewById(R.id.btn_retour);
        btnRetour.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        nomGroupe = view.findViewById(R.id.text_nom_groupe);
        listeMembres = view.findViewById(R.id.layout_membres);
        btnQuitter = view.findViewById(R.id.btn_quitter_groupe);
        btnSupprimer = view.findViewById(R.id.btn_supprimer_groupe);

        btnSupprimer.setVisibility(View.GONE);
        btnQuitter.setVisibility(View.GONE);
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (getArguments() != null) {
            groupId = getArguments().getString("groupId", "");
        }

        chargerInfosGroupe();
        return view;
    }

    private void chargerInfosGroupe() {
        FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) return;

                    nomGroupe.setText(documentSnapshot.getString("groupName"));

                    Map<String, Map<String, Object>> membres = (Map<String, Map<String, Object>>) documentSnapshot.get("members");
                    if (membres == null) return;

                    listeMembres.removeAllViews();

                    if (membres.containsKey(currentUserId)) {
                        currentUserRole = (String) membres.get(currentUserId).get("role");
                    } else {
                        currentUserRole = "Membre";
                    }

                    if ("Propriétaire".equals(currentUserRole)) {
                        btnSupprimer.setVisibility(View.VISIBLE);
                        btnSupprimer.setOnClickListener(v -> {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Supprimer le groupe")
                                    .setMessage("Cette action est irréversible. Confirmez-vous ?")
                                    .setPositiveButton("Oui", (dialog, which) -> supprimerGroupe())
                                    .setNegativeButton("Annuler", null)
                                    .show();
                        });
                    } else {
                        btnQuitter.setVisibility(View.VISIBLE);
                        btnQuitter.setOnClickListener(v -> {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Quitter le groupe")
                                    .setMessage("Souhaitez-vous vraiment quitter ce groupe ?")
                                    .setPositiveButton("Oui", (dialog, which) -> quitterGroupe())
                                    .setNegativeButton("Annuler", null)
                                    .show();
                        });
                    }

                    for (Map.Entry<String, Map<String, Object>> entry : membres.entrySet()) {
                        String uid = entry.getKey();
                        Map<String, Object> infos = entry.getValue();
                        String username = (String) infos.get("userName");
                        String phone = (String) infos.get("numero");
                        String role = (String) infos.get("role");

                        LinearLayout ligne = new LinearLayout(getContext());
                        ligne.setOrientation(LinearLayout.HORIZONTAL);
                        ligne.setPadding(0, 8, 0, 8);
                        ligne.setLayoutParams(new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));

                        TextView tv = new TextView(getContext());
                        tv.setText(username + " (" + phone + ") - " + role);
                        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                        ligne.addView(tv);

                        if ("Propriétaire".equals(currentUserRole) && !uid.equals(currentUserId)) {
                            Button btnChangerRole = new Button(getContext());
                            btnChangerRole.setText("Changer rôle");
                            btnChangerRole.setTextSize(12);
                            btnChangerRole.setOnClickListener(v -> afficherPopupChoixRole(uid, username, role));
                            ligne.addView(btnChangerRole);
                        }

                        if ("Administrateur".equals(currentUserRole) && "Membre".equals(role)) {
                            Button btnPromouvoir = new Button(getContext());
                            btnPromouvoir.setText("Promouvoir");
                            btnPromouvoir.setTextSize(12);
                            btnPromouvoir.setOnClickListener(v -> promouvoirEnAdmin(uid));
                            ligne.addView(btnPromouvoir);
                        }

                        listeMembres.addView(ligne);
                    }

                });
    }

    private void afficherPopupChoixRole(String uid, String username, String roleActuel) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Changer le rôle de " + username);

        final String[] roles = {"Membre", "Administrateur", "Propriétaire"};
        final int indexActuel = roleActuel.equals("Membre") ? 0 : roleActuel.equals("Administrateur") ? 1 : 2;

        builder.setSingleChoiceItems(roles, indexActuel, null);

        builder.setPositiveButton("Valider", (dialog, which) -> {
            AlertDialog alertDialog = (AlertDialog) dialog;
            int selected = alertDialog.getListView().getCheckedItemPosition();
            String nouveauRole = roles[selected];

            if (!nouveauRole.equals(roleActuel)) {
                if ("Propriétaire".equals(nouveauRole)) {
                    confirmerPassationProprietaire(uid);
                } else {
                    appliquerChangementRoleSimple(uid, nouveauRole);
                }
            }
        });

        builder.setNegativeButton("Annuler", null);
        builder.show();
    }

    private void appliquerChangementRoleSimple(String uid, String nouveauRole) {
        DocumentReference groupRef = FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId);

        groupRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) return;

            Map<String, Map<String, Object>> membres = (Map<String, Map<String, Object>>) snapshot.get("members");
            if (membres == null || !membres.containsKey(uid)) return;

            membres.get(uid).put("role", nouveauRole);

            groupRef.update("members", membres)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Rôle mis à jour", Toast.LENGTH_SHORT).show();
                        chargerInfosGroupe();
                    });
        });
    }

    private void confirmerPassationProprietaire(String nouveauUid) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Confirmer la passation")
                .setMessage("Êtes-vous sûr de vouloir transférer la propriété à cet utilisateur ? Vous deviendrez Administrateur.")
                .setPositiveButton("Confirmer", (dialog, which) -> appliquerPassationProprietaire(nouveauUid))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void appliquerPassationProprietaire(String nouveauUid) {
        DocumentReference groupRef = FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId);

        groupRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) return;

            Map<String, Map<String, Object>> membres = (Map<String, Map<String, Object>>) snapshot.get("members");
            if (membres == null) return;

            for (Map.Entry<String, Map<String, Object>> entry : membres.entrySet()) {
                if ("Propriétaire".equals(entry.getValue().get("role"))) {
                    entry.getValue().put("role", "Administrateur");
                }
            }

            if (membres.containsKey(nouveauUid)) {
                membres.get(nouveauUid).put("role", "Propriétaire");
            }

            groupRef.update("members", membres)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Nouveau propriétaire défini", Toast.LENGTH_SHORT).show();
                        requireActivity().getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, new GroupesFragment())
                                .commit();
                    });
        });
    }

    private void promouvoirEnAdmin(String uid) {
        DocumentReference groupRef = FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId);

        groupRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) return;

            Map<String, Map<String, Object>> membres = (Map<String, Map<String, Object>>) snapshot.get("members");
            if (membres == null || !membres.containsKey(uid)) return;

            membres.get(uid).put("role", "Administrateur");

            groupRef.update("members", membres)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Membre promu avec succès", Toast.LENGTH_SHORT).show();
                        chargerInfosGroupe();
                    })
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Erreur lors de la promotion", Toast.LENGTH_SHORT).show());
        });
    }

    private void quitterGroupe() {
        FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId)
                .update(
                        "members." + currentUserId, FieldValue.delete(),
                        "memberIds", FieldValue.arrayRemove(currentUserId)
                )
                .addOnSuccessListener(unused -> {
                    Toast.makeText(getContext(), "Vous avez quitté le groupe", Toast.LENGTH_SHORT).show();
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, new GroupesFragment())
                            .commit();
                });
    }

    private void supprimerGroupe() {
        FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId)
                .delete()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(getContext(), "Groupe supprimé", Toast.LENGTH_SHORT).show();
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, new GroupesFragment())
                            .commit();
                });
    }
}