package com.example.collabasket.ui.fragments;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.collabasket.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

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
            groupId = getArguments().getString("groupId");
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
                    if (documentSnapshot.exists()) {
                        String nom = documentSnapshot.getString("groupName");
                        nomGroupe.setText(nom != null ? nom : "Groupe");

                        List<Map<String, Object>> membres = (List<Map<String, Object>>) documentSnapshot.get("members");
                        if (membres != null) {
                            listeMembres.removeAllViews();

                            for (Map<String, Object> membre : membres) {
                                if (currentUserId.equals(membre.get("userId"))) {
                                    currentUserRole = membre.get("role") != null ? membre.get("role").toString() : "Membre";
                                    break;
                                }
                            }

                            for (Map<String, Object> membre : membres) {
                                String username = membre.get("userName") != null ? membre.get("userName").toString() : "(Nom inconnu)";
                                String numero = membre.get("numero") != null ? membre.get("numero").toString() : "(Numéro inconnu)";
                                String role = membre.get("role") != null ? membre.get("role").toString() : "Membre";

                                // Affichage des rôles avec les nouveaux noms
                                String roleLabel = "";
                                if ("Propriétaire".equals(role)) {
                                    roleLabel = "Propriétaire";
                                } else if ("Administrateur".equals(role)) {
                                    roleLabel = "Administrateur";
                                } else {
                                    roleLabel = "Membre";
                                }

                                LinearLayout ligne = new LinearLayout(getContext());
                                ligne.setOrientation(LinearLayout.HORIZONTAL);
                                ligne.setPadding(0, 8, 0, 8);
                                ligne.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT));

                                TextView textView = new TextView(getContext());
                                textView.setText(username + " - " + numero + " [" + roleLabel + "]");
                                textView.setLayoutParams(new LinearLayout.LayoutParams(
                                        0,
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                        1f));

                                ligne.addView(textView);

                                // Afficher les options pour "Propriétaire" ou "Administrateur"
                                if (currentUserRole != null
                                        && (currentUserRole.equals("Propriétaire") || currentUserRole.equals("Administrateur"))
                                        && !currentUserId.equals(membre.get("userId"))) {

                                    Button btnEdit = new Button(getContext());
                                    btnEdit.setText("Modifier");
                                    btnEdit.setTextSize(12f);
                                    btnEdit.setOnClickListener(v -> {
                                        String[] roles = {"Membre", "Administrateur"};
                                        int checkedItem = role.equals("Administrateur") ? 1 : 0;

                                        new AlertDialog.Builder(getContext())
                                                .setTitle("Changer le rôle de " + username)
                                                .setSingleChoiceItems(roles, checkedItem, null)
                                                .setPositiveButton("Valider", (dialog, which) -> {
                                                    ListView lw = ((AlertDialog) dialog).getListView();
                                                    String selectedRole = lw.getCheckedItemPosition() == 1 ? "Administrateur" : "Membre";

                                                    FirebaseFirestore.getInstance()
                                                            .collection("groups")
                                                            .document(groupId)
                                                            .get()
                                                            .addOnSuccessListener(snapshot -> {
                                                                List<Map<String, Object>> updatedMembres =
                                                                        (List<Map<String, Object>>) snapshot.get("members");

                                                                if (updatedMembres != null) {
                                                                    for (Map<String, Object> m : updatedMembres) {
                                                                        if (m.get("userId") != null &&
                                                                                m.get("userId").toString().equals(membre.get("userId").toString())) {
                                                                            m.put("role", selectedRole);
                                                                            break;
                                                                        }
                                                                    }

                                                                    FirebaseFirestore.getInstance()
                                                                            .collection("groups")
                                                                            .document(groupId)
                                                                            .update("members", updatedMembres)
                                                                            .addOnSuccessListener(u -> {
                                                                                Toast.makeText(getContext(), "Rôle mis à jour", Toast.LENGTH_SHORT).show();
                                                                                chargerInfosGroupe();
                                                                            });
                                                                }
                                                            });
                                                })
                                                .setNegativeButton("Annuler", null)
                                                .show();
                                    });

                                    ligne.addView(btnEdit);
                                }

                                listeMembres.addView(ligne);
                            }

                            // Si l'utilisateur est Propriétaire, on affiche le bouton "Supprimer"
                            if ("Propriétaire".equals(currentUserRole)) {
                                btnSupprimer.setVisibility(View.VISIBLE);
                                btnSupprimer.setOnClickListener(v -> {
                                    new AlertDialog.Builder(getContext())
                                            .setTitle("Supprimer le groupe")
                                            .setMessage("Êtes-vous sûr de vouloir supprimer ce groupe ? Cette action est irréversible.")
                                            .setPositiveButton("Supprimer", (dialog, which) -> {
                                                FirebaseFirestore.getInstance()
                                                        .collection("groups")
                                                        .document(groupId)
                                                        .delete()
                                                        .addOnSuccessListener(aVoid -> {
                                                            Toast.makeText(getContext(), "Groupe supprimé", Toast.LENGTH_SHORT).show();
                                                            requireActivity().getSupportFragmentManager()
                                                                    .beginTransaction()
                                                                    .replace(R.id.fragment_container, new GroupesFragment())
                                                                    .commit();
                                                        })
                                                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Erreur lors de la suppression", Toast.LENGTH_SHORT).show());
                                            })
                                            .setNegativeButton("Annuler", null)
                                            .show();
                                });
                            }

                            // Si l'utilisateur est Membre ou Administrateur, on affiche le bouton "Quitter"
                            if ("Membre".equals(currentUserRole) || "Administrateur".equals(currentUserRole)) {
                                btnQuitter.setVisibility(View.VISIBLE);
                                btnQuitter.setOnClickListener(v -> {
                                    new AlertDialog.Builder(getContext())
                                            .setTitle("Quitter le groupe")
                                            .setMessage("Souhaitez-vous vraiment quitter ce groupe ?")
                                            .setPositiveButton("Oui", (dialog, which) -> {
                                                // Retirer l'utilisateur du groupe
                                                FirebaseFirestore.getInstance()
                                                        .collection("groups")
                                                        .document(groupId)
                                                        .get()
                                                        .addOnSuccessListener(snapshot -> {
                                                            List<Map<String, Object>> members = (List<Map<String, Object>>) snapshot.get("members");
                                                            List<String> memberIds = (List<String>) snapshot.get("memberIds");

                                                            members.removeIf(m -> currentUserId.equals(m.get("userId")));
                                                            memberIds.removeIf(id1 -> id1.equals(currentUserId));

                                                            FirebaseFirestore.getInstance()
                                                                    .collection("groups")
                                                                    .document(groupId)
                                                                    .update("members", members, "memberIds", memberIds)
                                                                    .addOnSuccessListener(aVoid -> {
                                                                        Toast.makeText(getContext(), "Vous avez quitté le groupe", Toast.LENGTH_SHORT).show();
                                                                        requireActivity().getSupportFragmentManager()
                                                                                .beginTransaction()
                                                                                .replace(R.id.fragment_container, new GroupesFragment())
                                                                                .commit();
                                                                    });
                                                        });
                                            })
                                            .setNegativeButton("Annuler", null)
                                            .show();
                                });
                            }
                        }
                    }
                });
    }
}
