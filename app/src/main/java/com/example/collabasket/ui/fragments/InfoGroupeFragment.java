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
import com.google.firebase.firestore.FirebaseFirestore;

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

                    // Déterminer le rôle de l'utilisateur actuel
                    if (membres.containsKey(currentUserId)) {
                        currentUserRole = (String) membres.get(currentUserId).get("role");
                    }

                    // Afficher ou non les boutons selon le rôle
                    if ("Propriétaire".equals(currentUserRole)) {
                        btnSupprimer.setVisibility(View.VISIBLE);
                    } else {
                        btnQuitter.setVisibility(View.VISIBLE);
                    }

                    // Affichage dynamique des membres
                    for (Map.Entry<String, Map<String, Object>> entry : membres.entrySet()) {
                        String uid = entry.getKey();
                        Map<String, Object> infos = entry.getValue();
                        String username = (String) infos.get("username");
                        String phone = (String) infos.get("phone");
                        String role = (String) infos.get("role");

                        TextView tv = new TextView(getContext());
                        tv.setText(username + " (" + phone + ") - " + role);
                        listeMembres.addView(tv);
                    }
                });
    }
}