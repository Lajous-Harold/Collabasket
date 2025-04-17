package com.example.collabasket.utils;

import android.content.Context;
import android.widget.Toast;

import com.example.collabasket.R;
import com.example.collabasket.ui.fragments.GroupesFragment;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.fragment.app.FragmentActivity;

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
                .update("members." + userId + ".role", role);
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
}
