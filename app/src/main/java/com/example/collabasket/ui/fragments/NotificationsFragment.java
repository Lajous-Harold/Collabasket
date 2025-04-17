package com.example.collabasket.ui.fragments;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.Toast;
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.collabasket.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class NotificationsFragment extends Fragment {

    private Switch switchGlobal, switchProduitAjoute, switchProduitSupprime, switchMembreAjoute, switchGroupeCree;
    private FirebaseFirestore firestore;
    private String uid;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        firestore = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        switchGlobal = view.findViewById(R.id.switch_global);
        switchProduitAjoute = view.findViewById(R.id.switch_produit_ajoute);
        switchProduitSupprime = view.findViewById(R.id.switch_produit_supprime);
        switchMembreAjoute = view.findViewById(R.id.switch_membre_ajoute);
        switchGroupeCree = view.findViewById(R.id.switch_groupe_cree);

        // Vérification de la permission avant d'afficher les préférences
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // Demander la permission si non accordée
                ActivityCompat.requestPermissions(
                        requireActivity(),
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101 // Code de requête
                );
                return view; // Ne charge pas les préférences avant que la permission soit accordée
            }
        }

        chargerPreferences();

        return view;
    }

    private void chargerPreferences() {
        firestore.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.contains("notificationsSettings")) {
                        Map<String, Object> settings = (Map<String, Object>) doc.get("notificationsSettings");

                        switchGlobal.setChecked((Boolean) settings.getOrDefault("global", true));
                        switchProduitAjoute.setChecked((Boolean) settings.getOrDefault("produitAjoute", true));
                        switchProduitSupprime.setChecked((Boolean) settings.getOrDefault("produitSupprime", true));
                        switchMembreAjoute.setChecked((Boolean) settings.getOrDefault("membreAjoute", true));
                        switchGroupeCree.setChecked((Boolean) settings.getOrDefault("groupeCree", true));
                    }

                    setListeners();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Erreur lors du chargement des préférences", Toast.LENGTH_SHORT).show());
    }

    private void setListeners() {
        switchGlobal.setOnCheckedChangeListener((btn, isChecked) -> update("global", isChecked));
        switchProduitAjoute.setOnCheckedChangeListener((btn, isChecked) -> update("produitAjoute", isChecked));
        switchProduitSupprime.setOnCheckedChangeListener((btn, isChecked) -> update("produitSupprime", isChecked));
        switchMembreAjoute.setOnCheckedChangeListener((btn, isChecked) -> update("membreAjoute", isChecked));
        switchGroupeCree.setOnCheckedChangeListener((btn, isChecked) -> update("groupeCree", isChecked));
    }

    private void update(String champ, boolean valeur) {
        firestore.collection("users")
                .document(uid)
                .update("notificationsSettings." + champ, valeur);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission accordée, recharge les préférences
                chargerPreferences();
            } else {
                // Permission refusée, afficher un toast ou désactiver les fonctionnalités
                Toast.makeText(getContext(), "Les notifications n'ont pas été activées", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
