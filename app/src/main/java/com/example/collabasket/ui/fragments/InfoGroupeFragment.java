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
import com.example.collabasket.utils.GroupesUtils;
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
                    if (documentSnapshot.exists()) {
                        nomGroupe.setText(documentSnapshot.getString("groupName"));

                        List<Map<String, Object>> membres = (List<Map<String, Object>>) documentSnapshot.get("members");
                        listeMembres.removeAllViews();

                        for (Map<String, Object> membre : membres) {
                            if (currentUserId.equals(membre.get("userId"))) {
                                currentUserRole = (String) membre.get("role");
                                break;
                            }
                        }

                        for (Map<String, Object> membre : membres) {
                            String userId = (String) membre.get("userId");
                            String username = (String) membre.get("userName");
                            String numero = (String) membre.get("numero");
                            String role = (String) membre.get("role");

                            LinearLayout ligne = new LinearLayout(getContext());
                            ligne.setOrientation(LinearLayout.HORIZONTAL);
                            ligne.setPadding(0, 8, 0, 8);

                            TextView textView = new TextView(getContext());
                            textView.setText(username + " - " + numero + " [" + role + "]");
                            textView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                            ligne.addView(textView);

                            if (!currentUserId.equals(userId) &&
                                    ("Propriétaire".equals(currentUserRole) || ("Administrateur".equals(currentUserRole) && "Membre".equals(role)))) {
                                Button btnModifier = new Button(getContext());
                                btnModifier.setText("Modifier");
                                btnModifier.setOnClickListener(v -> afficherDialogueRole(userId, username, role));
                                ligne.addView(btnModifier);
                            }

                            listeMembres.addView(ligne);
                        }

                        if ("Propriétaire".equals(currentUserRole)) {
                            btnSupprimer.setVisibility(View.VISIBLE);
                            btnSupprimer.setOnClickListener(v -> GroupesUtils.supprimerGroupe(getContext(), groupId));
                        } else {
                            btnQuitter.setVisibility(View.VISIBLE);
                            btnQuitter.setOnClickListener(v -> GroupesUtils.quitterGroupe(getContext(), groupId, currentUserId));
                        }
                    }
                });
    }

    private void afficherDialogueRole(String userId, String username, String roleActuel) {
        String[] roles = {"Membre", "Administrateur", "Propriétaire"};

        new AlertDialog.Builder(getContext())
                .setTitle("Changer le rôle de " + username)
                .setSingleChoiceItems(roles, getRoleIndex(roleActuel, roles), null)
                .setPositiveButton("Valider", (dialog, which) -> {
                    ListView lw = ((AlertDialog) dialog).getListView();
                    String selectedRole = roles[lw.getCheckedItemPosition()];

                    if ("Propriétaire".equals(selectedRole) && "Propriétaire".equals(currentUserRole)) {
                        new AlertDialog.Builder(getContext())
                                .setTitle("Transférer la propriété")
                                .setMessage("Êtes-vous sûr de transférer la propriété à " + username + " ? Vous deviendrez administrateur.")
                                .setPositiveButton("Confirmer", (d, w) -> {
                                    GroupesUtils.transfererPropriete(groupId, userId, currentUserId);
                                    chargerInfosGroupe();
                                })
                                .setNegativeButton("Annuler", null)
                                .show();
                    } else {
                        GroupesUtils.modifierRole(groupId, userId, selectedRole);
                        chargerInfosGroupe();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private int getRoleIndex(String role, String[] roles) {
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equals(role)) return i;
        }
        return 0;
    }
}
