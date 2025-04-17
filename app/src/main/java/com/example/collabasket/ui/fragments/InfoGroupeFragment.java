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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InfoGroupeFragment extends Fragment {

    private String groupId;
    private String currentUserId;
    private String currentUserRole;
    private TextView nomGroupe;
    private LinearLayout listeMembres;
    private Button btnQuitter;
    private Button btnSupprimer;
    private Button btnGestionPermissions;

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
        btnGestionPermissions = view.findViewById(R.id.btn_gestion_permissions);
        btnGestionPermissions.setVisibility(View.GONE);

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

                    // Déterminer le rôle de l'utilisateur actuel
                    if (membres.containsKey(currentUserId)) {
                        currentUserRole = (String) membres.get(currentUserId).get("role");
                    } else {
                        currentUserRole = "Membre";
                    }

                    // Affichage des boutons selon le rôle
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

                    // Afficher le bouton de gestion des permissions uniquement pour Propriétaire
                    if ("Propriétaire".equals(currentUserRole)) {
                        btnGestionPermissions.setVisibility(View.VISIBLE);
                        btnGestionPermissions.setOnClickListener(v -> ouvrirInterfaceGestionPermissions(membres));
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

                        // Ajout du bouton uniquement si currentUser est Admin ET que le membre est un Membre
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
                        chargerInfosGroupe(); // Refresh visuel
                    })
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Erreur lors de la promotion", Toast.LENGTH_SHORT).show());
        });
    }
    private void ouvrirInterfaceGestionPermissions(Map<String, Map<String, Object>> membres) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Modifier les rôles");

        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);
        scroll.addView(layout);

        Map<String, String> modifications = new HashMap<>();
        final String[] nouveauProprietaire = {null};

        for (Map.Entry<String, Map<String, Object>> entry : membres.entrySet()) {
            String uid = entry.getKey();
            Map<String, Object> infos = entry.getValue();
            String username = (String) infos.get("userName");
            String roleActuel = (String) infos.get("role");

            if (uid.equals(currentUserId)) continue; // Ne pas gérer son propre rôle

            TextView label = new TextView(requireContext());
            label.setText(username + " - Actuel : " + roleActuel);
            layout.addView(label);

            List<String> choix = new ArrayList<>();
            if (!"Membre".equals(roleActuel)) choix.add("Membre");
            if (!"Administrateur".equals(roleActuel)) choix.add("Administrateur");
            if (!"Propriétaire".equals(roleActuel)) choix.add("Propriétaire");

            Spinner spinner = new Spinner(requireContext());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, choix);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setTag(uid + "|" + roleActuel);
            layout.addView(spinner);
        }

        builder.setView(scroll);

        builder.setPositiveButton("Valider", (dialog, which) -> {
            for (int i = 0; i < layout.getChildCount(); i++) {
                View v = layout.getChildAt(i);
                if (v instanceof Spinner) {
                    Spinner sp = (Spinner) v;
                    String tag = (String) sp.getTag();
                    String[] parts = tag.split("\\|");
                    String uid = parts[0];
                    String ancien = parts[1];

                    String selection = (String) sp.getSelectedItem();
                    if (!selection.equals(ancien)) {
                        if ("Propriétaire".equals(selection)) {
                            nouveauProprietaire[0] = uid;
                        }
                        modifications.put(uid, selection);
                    }
                }
            }

            appliquerChangementsDeRoles(modifications, nouveauProprietaire[0]);
        });

        builder.setNegativeButton("Annuler", null);
        builder.show();
    }
    private void appliquerChangementsDeRoles(Map<String, String> changements, @Nullable String nouveauProprietaireId) {
        DocumentReference groupRef = FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId);

        groupRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) return;

            Map<String, Map<String, Object>> membres = (Map<String, Map<String, Object>>) snapshot.get("members");
            if (membres == null) return;

            // Si un nouveau propriétaire est désigné, rétrograder l'ancien
            if (nouveauProprietaireId != null) {
                for (Map.Entry<String, Map<String, Object>> entry : membres.entrySet()) {
                    String uid = entry.getKey();
                    String role = (String) entry.getValue().get("role");
                    if ("Propriétaire".equals(role)) {
                        membres.get(uid).put("role", "Administrateur");
                    }
                }
                membres.get(nouveauProprietaireId).put("role", "Propriétaire");
            }

            // Appliquer les autres changements
            for (Map.Entry<String, String> entry : changements.entrySet()) {
                String uid = entry.getKey();
                String nouveauRole = entry.getValue();
                if (!"Propriétaire".equals(nouveauRole)) {
                    membres.get(uid).put("role", nouveauRole);
                }
            }

            groupRef.update("members", membres)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Permissions mises à jour", Toast.LENGTH_SHORT).show();
                        chargerInfosGroupe(); // refresh affichage
                    })
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Erreur lors de la mise à jour", Toast.LENGTH_SHORT).show());
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