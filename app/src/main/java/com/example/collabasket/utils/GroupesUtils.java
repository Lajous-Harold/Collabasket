package com.example.collabasket.utils;

import android.content.Context;
import android.widget.Toast;

import com.example.collabasket.R;
import com.example.collabasket.ui.fragments.GroupesFragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

import androidx.fragment.app.FragmentActivity;

import java.util.List;
import java.util.Map;

public class GroupesUtils {

    public static void promoteToAdmin(String groupId, String userId) {
        FirebaseFirestore.getInstance().collection("groups")
                .document(groupId)
                .update("members." + userId + ".role", "Administrateur");
    }

    public static void demoteToMember(String groupId, String userId) {
        FirebaseFirestore.getInstance().collection("groups")
                .document(groupId)
                .update("members." + userId + ".role", "Membre");
    }

    public static void transfererPropriete(String groupId, String oldOwnerId, String newOwnerId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("groups").document(groupId)
                .update("ownerId", newOwnerId,
                        "members." + newOwnerId + ".role", "Propriétaire",
                        "members." + oldOwnerId + ".role", "Administrateur");
    }

    public static void modifierRole(String groupId, String userId, String role) {
        FirebaseFirestore.getInstance().collection("groups")
                .document(groupId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Map<String, Object>> membres = (List<Map<String, Object>>) snapshot.get("members");
                    if (membres != null) {
                        for (Map<String, Object> m : membres) {
                            if (userId.equals(m.get("userId"))) {
                                m.put("role", role);
                                break;
                            }
                        }
                        FirebaseFirestore.getInstance().collection("groups")
                                .document(groupId)
                                .update("members", membres);
                    }
                });
    }

    public static void supprimerGroupe(Context context, String groupId) {
        FirebaseFirestore.getInstance()
                .collection("groups")
                .document(groupId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(context, "Groupe supprimé", Toast.LENGTH_SHORT).show();
                    if (context instanceof FragmentActivity) {
                        FragmentActivity activity = (FragmentActivity) context;
                        activity.getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, new GroupesFragment())
                                .commit();
                    }
                });
    }

    public static void quitterGroupe(Context context, String groupId, String userId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("groups").document(groupId).get()
                .addOnSuccessListener((DocumentSnapshot snapshot) -> {
                    List<Map<String, Object>> members = (List<Map<String, Object>>) snapshot.get("members");
                    List<String> memberIds = (List<String>) snapshot.get("memberIds");

                    if (members != null) {
                        members.removeIf(m -> userId.equals(m.get("userId")));
                    }
                    if (memberIds != null) {
                        memberIds.removeIf(id -> id.equals(userId));
                    }

                    db.collection("groups")
                            .document(groupId)
                            .update("members", members, "memberIds", memberIds)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(context, "Vous avez quitté le groupe", Toast.LENGTH_SHORT).show();
                                if (context instanceof FragmentActivity) {
                                    FragmentActivity activity = (FragmentActivity) context;
                                    activity.getSupportFragmentManager()
                                            .beginTransaction()
                                            .replace(R.id.fragment_container, new GroupesFragment())
                                            .commit();
                                }
                            });
                });
    }
}