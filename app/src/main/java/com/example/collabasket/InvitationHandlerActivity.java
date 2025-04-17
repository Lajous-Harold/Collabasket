package com.example.collabasket;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.Map;

public class InvitationHandlerActivity extends AppCompatActivity {

    private String groupId;
    private String currentUid;
    private TextView textMessage;
    private Button btnJoin;
    private Button btnRefuser
    private ProgressBar progressBar;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invitation_handler);

        textMessage = findViewById(R.id.text_invite_message);
        btnJoin = findViewById(R.id.btn_join_group);
        btnRefuser = findViewById(R.id.btn_refuser_invite);
        progressBar = findViewById(R.id.progress_bar);
        db = FirebaseFirestore.getInstance();

        groupId = getIntent().getStringExtra("groupId");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Log.d("INVITE", "Utilisateur non connecté");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUid = user.getUid();
        checkMembershipAndShowUI();
    }

    private void checkMembershipAndShowUI() {
        progressBar.setVisibility(View.VISIBLE);
        db.collection("groups").document(groupId).get()
                .addOnSuccessListener(snapshot -> {
                    progressBar.setVisibility(View.GONE);
                    if (!snapshot.exists()) {
                        textMessage.setText("Ce groupe n'existe pas.");
                        btnJoin.setVisibility(View.GONE);
                        btnRefuser.setVisibility(View.GONE);
                        return;
                    }

                    String groupName = snapshot.getString("groupName");
                    textMessage.setText("Rejoindre " + groupName + " ?");

                    if (snapshot.contains("memberIds")) {
                        @SuppressWarnings("unchecked")
                        java.util.List<String> memberIds = (java.util.List<String>) snapshot.get("memberIds");
                        if (memberIds != null && memberIds.contains(currentUid)) {
                            textMessage.setText("Vous êtes déjà membre du groupe.");
                            btnJoin.setVisibility(View.GONE);
                            btnRefuser.setVisibility(View.GONE);
                            return;
                        }
                    }

                    // ✅ Affiche les deux boutons
                    btnJoin.setVisibility(View.VISIBLE);
                    btnRefuser.setVisibility(View.VISIBLE);

                    btnJoin.setOnClickListener(v -> ajouterUtilisateurAuGroupe(snapshot));
                    btnRefuser.setOnClickListener(v -> {
                        Toast.makeText(this, "Invitation refusée", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    textMessage.setText("Erreur lors de l'accès au groupe.");
                    btnJoin.setVisibility(View.GONE);
                    btnRefuser.setVisibility(View.GONE);
                    Log.e("INVITE", "Erreur : ", e);
                });
    }

    private void ajouterUtilisateurAuGroupe(DocumentSnapshot groupSnapshot) {
        btnJoin.setEnabled(false);
        btnJoin.setText("Ajout...");

        db.collection("users").document(currentUid).get()
                .addOnSuccessListener(userSnap -> {
                    String username = userSnap.contains("username") ? userSnap.getString("username") : "Inconnu";
                    String numero = userSnap.contains("phone") ? userSnap.getString("phone") : "";

                    Map<String, Object> nouveauMembre = new HashMap<>();
                    nouveauMembre.put("userId", currentUid);
                    nouveauMembre.put("userName", username);
                    nouveauMembre.put("numero", numero);
                    nouveauMembre.put("role", "Membre");

                    // ✅ Nouvelle structure : Map<String, Object> pour les membres
                    @SuppressWarnings("unchecked")
                    Map<String, Object> members = (Map<String, Object>) groupSnapshot.get("members");
                    if (members == null) members = new HashMap<>();
                    members.put(currentUid, nouveauMembre);

                    @SuppressWarnings("unchecked")
                    java.util.List<String> memberIds = (java.util.List<String>) groupSnapshot.get("memberIds");
                    if (memberIds == null) memberIds = new java.util.ArrayList<>();
                    if (!memberIds.contains(currentUid)) {
                        memberIds.add(currentUid);
                    }

                    db.collection("groups").document(groupId)
                            .update("members", members, "memberIds", memberIds)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Vous avez rejoint le groupe !", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(this, MainActivity.class);
                                intent.putExtra("redirectToGroupId", groupId);
                                startActivity(intent);
                                finish();
                            });
                });
    }
}
